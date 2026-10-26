package br.com.gestaodeestoque.service;

import br.com.gestaodeestoque.model.Produto;
import br.com.gestaodeestoque.repository.ProdutosRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ProdutoService {

    private final ProdutosRepository produtosRepository;

    public ProdutoService(ProdutosRepository produtosRepository) {
        this.produtosRepository = produtosRepository;
    }

    @Transactional
    public Produto cadastrarProduto(Produto produto) {
        produto.setId(null);
        return produtosRepository.save(produto);
    }

    @Transactional(readOnly = true)
    public List<Produto> obterTodosProdutos() {
        return produtosRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Produto obterProdutoPorId(@Valid Long id) {
        return produtosRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Produto não encontrado"
                ));
    }

    @Transactional
    public Produto atualizaProduto(Long id, Produto dadosProdutos) {

        Produto produto = obterProdutoPorId(id);

        produto.setNome(dadosProdutos.getNome());
        produto.setDescricao(dadosProdutos.getDescricao());
        produto.setCategoria(dadosProdutos.getCategoria());
        produto.setPreco(dadosProdutos.getPreco());
        produto.setQuantidade(dadosProdutos.getQuantidade());

        return produtosRepository.save(produto);
    }

    @Transactional
    public void deletaProduto(Long id) {

    }
}
