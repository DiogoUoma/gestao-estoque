package br.com.gestaodeestoque.controller;

import br.com.gestaodeestoque.model.Produto;
import br.com.gestaodeestoque.service.ImportacaoProdutoService;
import br.com.gestaodeestoque.service.ProdutoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("/produtos")
@CrossOrigin(origins = "http://localhost:5173")
public class ProdutoController {

    private final ProdutoService produtoService;

    private final ImportacaoProdutoService importacaoProdutoService;

    public ProdutoController(ProdutoService produtoService, ImportacaoProdutoService importacaoProdutoService) {
        this.produtoService = produtoService;
        this.importacaoProdutoService = importacaoProdutoService;
    }

    @GetMapping("/hello")
    public String hello() {
        return "API is working";
    }

    @PostMapping
    public ResponseEntity<Produto> cadastrarProduto(@RequestBody @Valid Produto produto) {
        Produto produtoSalvo = produtoService.cadastrarProduto(produto);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(produtoSalvo);
    }

    @GetMapping
    public ResponseEntity<List<Produto>> obterTodosProdutos() {
        return ResponseEntity.ok(produtoService.obterTodosProdutos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Produto> obterProdutoPorId(@PathVariable @Valid Long id) {
        Produto produto = produtoService.obterProdutoPorId(id);
        return ResponseEntity.ok(produto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Produto> atualizaProduto(@PathVariable Long id, @RequestBody @Valid Produto produto){
        Produto produtoAtualizado = produtoService.atualizaProduto(id, produto);
        return ResponseEntity.ok(produtoAtualizado);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarProduto(@PathVariable Long id) {
        produtoService.deletaProduto(id);

        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> importarProdutos(@RequestParam("arquivo")MultipartFile arquivo) {
        int quantidade = importacaoProdutoService.importar(arquivo);

        return ResponseEntity.of(Optional.of(Map.of("mensagem", "Produtos importados com sucesso", "quantidade importada", quantidade
        )));
    }


}
