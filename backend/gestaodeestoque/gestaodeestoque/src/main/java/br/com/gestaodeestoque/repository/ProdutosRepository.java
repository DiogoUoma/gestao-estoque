package br.com.gestaodeestoque.repository;

import br.com.gestaodeestoque.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProdutosRepository extends JpaRepository<Produto, Long> {
}
