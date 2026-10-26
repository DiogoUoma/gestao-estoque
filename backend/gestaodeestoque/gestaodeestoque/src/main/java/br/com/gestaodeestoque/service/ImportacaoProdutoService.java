package br.com.gestaodeestoque.service;

import br.com.gestaodeestoque.enums.Categoria;
import br.com.gestaodeestoque.model.Produto;
import br.com.gestaodeestoque.repository.ProdutosRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ImportacaoProdutoService {

    private static final int LIMITE_PRODUTOS = 5000;

    private static final List<String> COLUNAS = List.of(
            "nome", "descricao", "categoria", "preco", "quantidade"
    );

    private final ProdutosRepository produtosRepository;

    public ImportacaoProdutoService(ProdutosRepository produtosRepository) {
        this.produtosRepository = produtosRepository;
    }

    @Transactional
    public int importar(MultipartFile arquivo) {
        if (arquivo.isEmpty()) {
            throw erro("O arquivo está vazio.");
        }

        String nome = arquivo.getOriginalFilename();

        if (nome == null) {
            throw erro("O arquivo precisa ter um nome.");
        }

        nome = nome.toLowerCase(Locale.ROOT);

        if (!nome.endsWith(".csv") && !nome.endsWith(".xlsx")) {
            throw erro("Envie um arquivo .csv ou .xlsx.");
        }

        List<Produto> produtos;

        // Apenas a leitura fica neste bloco.
        // Erros de persistência não são tratados como erros de arquivo.
        try {
            produtos = nome.endsWith(".csv")
                    ? lerCsv(arquivo)
                    : lerExcel(arquivo);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Não foi possível ler o arquivo. Confira seu formato. "
                            + "Para CSV, utilize UTF-8 e separador ponto e vírgula.",
                    e
            );
        }

        if (produtos.isEmpty()) {
            throw erro("O arquivo não contém produtos.");
        }

        produtosRepository.saveAll(produtos);

        return produtos.size();
    }

    private List<Produto> lerCsv(MultipartFile arquivo) throws IOException {
        List<Produto> produtos = new ArrayList<>();

        CSVFormat formato = CSVFormat.DEFAULT.builder()
                .setDelimiter(';')
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .get();

        // REPORT impede que caracteres inválidos sejam substituídos silenciosamente.
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        try (
                PushbackReader reader = new PushbackReader(
                        new InputStreamReader(arquivo.getInputStream(), decoder), 1
                )
        ) {
            // Remove o BOM que alguns programas adicionam ao CSV UTF-8.
            int primeiro = reader.read();

            if (primeiro != -1 && primeiro != '\uFEFF') {
                reader.unread(primeiro);
            }

            try (CSVParser parser = formato.parse(reader)) {
                boolean cabecalhoLido = false;

                for (CSVRecord registro : parser) {
                    List<String> valores = new ArrayList<>();
                    registro.forEach(valores::add);

                    if (!cabecalhoLido) {
                        validarCabecalho(valores);
                        cabecalhoLido = true;
                        continue;
                    }

                    adicionarProduto(
                            produtos,
                            valores,
                            "Registro " + registro.getRecordNumber() + " do CSV"
                    );
                }

                if (!cabecalhoLido) {
                    throw erro("O CSV não contém cabeçalho.");
                }
            }
        }

        return produtos;
    }

    private List<Produto> lerExcel(MultipartFile arquivo) throws IOException {
        List<Produto> produtos = new ArrayList<>();

        try (
                InputStream input = arquivo.getInputStream();
                Workbook workbook = new XSSFWorkbook(input)
        ) {
            if (workbook.getNumberOfSheets() == 0) {
                throw erro("O Excel não contém abas.");
            }

            Sheet aba = workbook.getSheetAt(0);
            Row cabecalho = aba.getRow(0);

            if (cabecalho == null) {
                throw erro("A primeira linha deve conter o cabeçalho.");
            }

            validarCabecalho(lerLinhaExcel(cabecalho));

            for (Row linha : aba) {
                if (linha.getRowNum() == 0) {
                    continue;
                }

                adicionarProduto(
                        produtos,
                        lerLinhaExcel(linha),
                        "Linha " + (linha.getRowNum() + 1) + " do Excel"
                );
            }
        }

        return produtos;
    }

    private List<String> lerLinhaExcel(Row linha) {
        List<String> valores = new ArrayList<>();

        // Lê pelo menos as cinco colunas obrigatórias.
        int totalColunas = Math.max(COLUNAS.size(), linha.getLastCellNum());

        for (int coluna = 0; coluna < totalColunas; coluna++) {
            Cell celula = linha.getCell(
                    coluna,
                    Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
            );

            valores.add(lerCelula(celula, linha.getRowNum() + 1));
        }

        // Desconsidera colunas extras vazias que tenham apenas formatação.
        while (valores.size() > COLUNAS.size()
                && valores.get(valores.size() - 1).isBlank()) {
            valores.remove(valores.size() - 1);
        }

        return valores;
    }

    private String lerCelula(Cell celula, int linha) {
        if (celula == null) {
            return "";
        }

        return switch (celula.getCellType()) {
            case STRING -> celula.getStringCellValue().trim();
            case BLANK -> "";

            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(celula)) {
                    throw erro("Linha " + linha + ": datas não são aceitas.");
                }

                // Lê o valor numérico, sem arredondá-lo pela formatação visual.
                yield BigDecimal.valueOf(celula.getNumericCellValue())
                        .stripTrailingZeros()
                        .toPlainString();
            }

            default -> throw erro(
                    "Linha " + linha
                            + ": use apenas texto ou números, sem fórmulas."
            );
        };
    }

    private void validarCabecalho(List<String> valores) {
        List<String> cabecalho = valores.stream()
                .map(String::trim)
                .map(valor -> valor.toLowerCase(Locale.ROOT))
                .toList();

        if (!cabecalho.equals(COLUNAS)) {
            throw erro(
                    "O cabeçalho deve conter, nesta ordem: "
                            + String.join("; ", COLUNAS)
            );
        }
    }

    private void adicionarProduto(
            List<Produto> produtos,
            List<String> valores,
            String local
    ) {
        if (valores.stream().allMatch(String::isBlank)) {
            return;
        }

        if (valores.size() != COLUNAS.size()) {
            throw erro(local + ": informe exatamente cinco colunas.");
        }

        if (produtos.size() >= LIMITE_PRODUTOS) {
            throw erro("O limite é de " + LIMITE_PRODUTOS + " produtos por arquivo.");
        }

        String nome = valores.get(0).trim();
        String descricao = valores.get(1).trim();
        String categoriaTexto = valores.get(2).trim();
        String precoTexto = valores.get(3).trim().replace(',', '.');
        String quantidadeTexto = valores.get(4).trim();

        if (nome.isBlank() || nome.length() > 150) {
            throw erro(local + ": nome obrigatório, com até 150 caracteres.");
        }

        if (descricao.length() > 500) {
            throw erro(local + ": descrição deve ter até 500 caracteres.");
        }

        Categoria categoria;

        try {
            categoria = Categoria.valueOf(
                    categoriaTexto.toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException e) {
            throw erro(
                    local + ": categoria inválida. Use BEBIDA, ALIMENTO, "
                            + "ALIMENTO_PREPARADO ou LEMBRANCINHA."
            );
        }

        // Até dez dígitos inteiros e duas casas decimais:
        // corresponde ao precision = 12, scale = 2 da entidade.
        if (!precoTexto.matches("\\d{1,10}(\\.\\d{1,2})?")) {
            throw erro(
                    local + ": preço inválido. Use um valor não negativo, "
                            + "com até dez dígitos inteiros e duas casas decimais."
            );
        }

        BigDecimal preco = new BigDecimal(precoTexto);
        int quantidade;

        try {
            quantidade = Integer.parseInt(quantidadeTexto);
        } catch (NumberFormatException e) {
            throw erro(local + ": quantidade deve ser um número inteiro válido.");
        }

        if (quantidade < 0) {
            throw erro(local + ": quantidade não pode ser negativa.");
        }

        Produto produto = new Produto();
        produto.setNome(nome);
        produto.setDescricao(descricao.isBlank() ? null : descricao);
        produto.setCategoria(categoria);
        produto.setPreco(preco);
        produto.setQuantidade(quantidade);

        produtos.add(produto);
    }

    private ResponseStatusException erro(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }
}