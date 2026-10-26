import { useEffect, useState, useRef } from "react";
import type { ChangeEvent, FormEvent } from "react";
import "./App.css";

const categorias = [
  "BEBIDA",
  "ALIMENTO",
  "ALIMENTO_PREPARADO",
  "LEMBRANCINHA",
] as const;

type Categoria = (typeof categorias)[number];

type Produto = {
  id: number;
  nome: string;
  descricao: string | null;
  categoria: Categoria;
  preco: number;
  quantidade: number;
};

type FormularioProduto = {
  nome: string;
  descricao: string;
  categoria: Categoria;
  preco: string;
  quantidade: string;
};

const formularioInicial: FormularioProduto = {
  nome: "",
  descricao: "",
  categoria: "BEBIDA",
  preco: "",
  quantidade: "",
};

const API_URL = import.meta.env.VITE_API_URL;

const moeda = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
});

function nomeCategoria(categoria: Categoria) {
  return categoria.replaceAll("_", " ");
}

async function verificarResposta(resposta: Response) {
  if (resposta.ok) return;

  let mensagem = `Não foi possível concluir a operação. HTTP ${resposta.status}`;

  // Tenta aproveitar a mensagem enviada pelo backend.
  try {
    const dados = await resposta.json();

    if (typeof dados.message === "string") {
      mensagem = dados.message;
    } else if (typeof dados.mensagem === "string") {
      mensagem = dados.mensagem;
    } else if (typeof dados.detail === "string") {
      mensagem = dados.detail;
    }
  } catch {
    // Mantém a mensagem padrão caso a resposta não seja JSON.
  }

  throw new Error(mensagem);
}

export default function App() {
  const [produtos, setProdutos] = useState<Produto[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [salvando, setSalvando] = useState(false);
  const [excluindoId, setExcluindoId] = useState<number | null>(null);

  const inputArquivoRef = useRef<HTMLInputElement>(null);
  const [importando, setImportando] = useState(false);

  const [erro, setErro] = useState<string | null>(null);
  const [erroFormulario, setErroFormulario] = useState<string | null>(null);
  const [mensagem, setMensagem] = useState<string | null>(null);

  const [tentativa, setTentativa] = useState(0);
  const [formularioAberto, setFormularioAberto] = useState(false);
  const [produtoEditandoId, setProdutoEditandoId] = useState<number | null>(
    null,
  );
  const [formulario, setFormulario] =
    useState<FormularioProduto>(formularioInicial);

  const ocupado = salvando || excluindoId !== null || importando;

  useEffect(() => {
    const controller = new AbortController();

    async function carregarProdutos() {
      setCarregando(true);
      setErro(null);

      try {
        const resposta = await fetch(`${API_URL}/produtos`, {
          signal: controller.signal,
        });

        await verificarResposta(resposta);

        const dados: Produto[] = await resposta.json();

        if (!controller.signal.aborted) {
          setProdutos(dados);
        }
      } catch (error) {
        if (controller.signal.aborted) return;

        setErro(
          error instanceof Error
            ? error.message
            : "Ocorreu um erro ao carregar os produtos.",
        );
      } finally {
        if (!controller.signal.aborted) {
          setCarregando(false);
        }
      }
    }

    void carregarProdutos();

    return () => controller.abort();
  }, [tentativa]);

  function atualizarCampo<K extends keyof FormularioProduto>(
    campo: K,
    valor: FormularioProduto[K],
  ) {
    setFormulario((atual) => ({
      ...atual,
      [campo]: valor,
    }));
  }

  function abrirCadastro() {
    setProdutoEditandoId(null);
    setFormulario({ ...formularioInicial });
    setErroFormulario(null);
    setMensagem(null);
    setFormularioAberto(true);
  }

  function abrirEdicao(produto: Produto) {
    setProdutoEditandoId(produto.id);

    setFormulario({
      nome: produto.nome,
      descricao: produto.descricao ?? "",
      categoria: produto.categoria,
      preco: String(produto.preco),
      quantidade: String(produto.quantidade),
    });

    setErroFormulario(null);
    setMensagem(null);
    setFormularioAberto(true);
  }

  function fecharFormulario() {
    setFormularioAberto(false);
    setProdutoEditandoId(null);
    setFormulario({ ...formularioInicial });
    setErroFormulario(null);
  }

  async function salvarProduto(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (ocupado) return;

    const nome = formulario.nome.trim();
    const preco = Number(formulario.preco);
    const quantidade = Number(formulario.quantidade);

    if (!nome) {
      setErroFormulario("Informe o nome do produto.");
      return;
    }

    if (
      formulario.preco.trim() === "" ||
      !Number.isFinite(preco) ||
      preco < 0
    ) {
      setErroFormulario("Informe um preço válido.");
      return;
    }

    if (
      formulario.quantidade.trim() === "" ||
      !Number.isSafeInteger(quantidade) ||
      quantidade < 0
    ) {
      setErroFormulario(
        "A quantidade deve ser um número inteiro não negativo.",
      );
      return;
    }

    const editando = produtoEditandoId !== null;

    const url = editando
      ? `${API_URL}/produtos/${produtoEditandoId}`
      : `${API_URL}/produtos`;

    setSalvando(true);
    setErroFormulario(null);
    setMensagem(null);

    try {
      const resposta = await fetch(url, {
        method: editando ? "PUT" : "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          nome,
          descricao: formulario.descricao.trim() || null,
          categoria: formulario.categoria,
          preco,
          quantidade,
        }),
      });

      await verificarResposta(resposta);

      fecharFormulario();

      setMensagem(
        editando
          ? "Produto atualizado com sucesso."
          : "Produto cadastrado com sucesso.",
      );

      setTentativa((valor) => valor + 1);
    } catch (error) {
      setErroFormulario(
        error instanceof Error
          ? error.message
          : "Ocorreu um erro ao salvar o produto.",
      );
    } finally {
      setSalvando(false);
    }
  }

  async function importarProdutos(event: ChangeEvent<HTMLInputElement>) {
    const input = event.currentTarget;
    const arquivo = input.files?.[0];

    if (!arquivo || ocupado) return;

    const extensao = arquivo.name.split(".").pop()?.toLowerCase();

    if (extensao !== "csv" && extensao !== "xlsx") {
      setErro("Selecione um arquivo .csv ou .xlsx.");
      input.value = "";
      return;
    }

    if (arquivo.size === 0) {
      setErro("O arquivo selecionado está vazio.");
      input.value = "";
      return;
    }

    const dados = new FormData();
    dados.append("arquivo", arquivo);

    setImportando(true);
    setErro(null);
    setMensagem(null);

    try {
      const resposta = await fetch(`${API_URL}/produtos/importar`, {
        method: "POST",
        body: dados,
      });

      await verificarResposta(resposta);

      const resultado = await resposta.json();
      const quantidade = resultado["quantidade importada"];

      setMensagem(
        typeof quantidade === "number"
          ? `Importação concluída: ${quantidade} produto(s) importado(s).`
          : "Produtos importados com sucesso.",
      );

      // Busca novamente os produtos para atualizar a tabela.
      setTentativa((valor) => valor + 1);
    } catch (error) {
      setErro(
        error instanceof Error
          ? error.message
          : "Não foi possível importar os produtos.",
      );
    } finally {
      setImportando(false);

      // Permite selecionar o mesmo arquivo novamente.
      input.value = "";
    }
  }

  async function excluirProduto(produto: Produto) {
    if (ocupado) return;

    const confirmou = window.confirm(
      `Deseja excluir o produto "${produto.nome}"? Essa ação não pode ser desfeita.`,
    );

    if (!confirmou) return;

    setExcluindoId(produto.id);
    setErro(null);
    setMensagem(null);

    try {
      const resposta = await fetch(`${API_URL}/produtos/${produto.id}`, {
        method: "DELETE",
      });

      await verificarResposta(resposta);

      // O DELETE retorna 204, portanto não chamamos resposta.json().
      setProdutos((atuais) => atuais.filter((item) => item.id !== produto.id));

      if (produtoEditandoId === produto.id) {
        fecharFormulario();
      }

      setMensagem("Produto excluído com sucesso.");
    } catch (error) {
      setErro(
        error instanceof Error
          ? error.message
          : "Ocorreu um erro ao excluir o produto.",
      );
    } finally {
      setExcluindoId(null);
    }
  }

  return (
    <main className="pagina">
      <header className="cabecalho">
        <div>
          <p className="subtitulo">Controle de estoque</p>
          <h1>Produtos</h1>
          <p>Gerencie os produtos cadastrados na loja.</p>
        </div>

        <div className="acoes">
          <button type="button" disabled={ocupado} onClick={abrirCadastro}>
            Novo produto
          </button>

          <input
            ref={inputArquivoRef}
            type="file"
            accept=".csv,.xlsx"
            hidden
            disabled={ocupado || carregando}
            onChange={(event) => void importarProdutos(event)}
          />

          <button
            type="button"
            disabled={ocupado || carregando}
            onClick={() => inputArquivoRef.current?.click()}
          >
            {importando ? "Importando..." : "Importar CSV / Excel"}
          </button>

          <button
            type="button"
            disabled={carregando || ocupado}
            onClick={() => setTentativa((valor) => valor + 1)}
          >
            {carregando ? "Carregando..." : "Atualizar"}
          </button>
        </div>
      </header>

      {mensagem && (
        <p className="sucesso" role="status">
          {mensagem}
        </p>
      )}

      {formularioAberto && (
        <section className="painel formulario-painel">
          <h2>
            {produtoEditandoId !== null ? "Editar produto" : "Novo produto"}
          </h2>

          <form onSubmit={salvarProduto}>
            <fieldset className="campos-produto" disabled={salvando}>
              <label htmlFor="nome">
                Nome
                <input
                  id="nome"
                  type="text"
                  maxLength={150}
                  required
                  value={formulario.nome}
                  onChange={(event) =>
                    atualizarCampo("nome", event.target.value)
                  }
                />
              </label>

              <label htmlFor="categoria">
                Categoria
                <select
                  id="categoria"
                  value={formulario.categoria}
                  onChange={(event) =>
                    atualizarCampo("categoria", event.target.value as Categoria)
                  }
                >
                  {categorias.map((categoria) => (
                    <option key={categoria} value={categoria}>
                      {nomeCategoria(categoria)}
                    </option>
                  ))}
                </select>
              </label>

              <label htmlFor="preco">
                Preço (R$)
                <input
                  id="preco"
                  type="number"
                  min="0"
                  max="9999999999.99"
                  step="0.01"
                  required
                  value={formulario.preco}
                  onChange={(event) =>
                    atualizarCampo("preco", event.target.value)
                  }
                />
              </label>

              <label htmlFor="quantidade">
                Quantidade
                <input
                  id="quantidade"
                  type="number"
                  min="0"
                  step="1"
                  required
                  value={formulario.quantidade}
                  onChange={(event) =>
                    atualizarCampo("quantidade", event.target.value)
                  }
                />
              </label>

              <label htmlFor="descricao" className="campo-descricao">
                Descrição
                <textarea
                  id="descricao"
                  rows={3}
                  maxLength={500}
                  value={formulario.descricao}
                  onChange={(event) =>
                    atualizarCampo("descricao", event.target.value)
                  }
                />
              </label>
            </fieldset>

            {erroFormulario && (
              <p className="erro" role="alert">
                {erroFormulario}
              </p>
            )}

            <div className="acoes">
              <button type="submit" disabled={ocupado}>
                {salvando ? "Salvando..." : "Salvar produto"}
              </button>

              <button
                type="button"
                disabled={salvando}
                onClick={fecharFormulario}
              >
                Cancelar
              </button>
            </div>
          </form>
        </section>
      )}

      {erro && (
        <div className="erro" role="alert">
          <p>{erro}</p>
        </div>
      )}

      {carregando ? (
        <p role="status">Carregando produtos...</p>
      ) : produtos.length === 0 ? (
        <div className="vazio">
          {erro
            ? "Não foi possível exibir os produtos. Tente atualizar."
            : "Nenhum produto cadastrado."}
        </div>
      ) : (
        <section className="painel" aria-label="Produtos cadastrados">
          <div className="resumo">
            <strong>{produtos.length}</strong> produto(s) cadastrado(s)
          </div>

          <div className="tabela-container">
            <table>
              <thead>
                <tr>
                  <th scope="col">Produto</th>
                  <th scope="col">Categoria</th>
                  <th scope="col" className="numero">
                    Preço
                  </th>
                  <th scope="col" className="numero">
                    Quantidade
                  </th>
                  <th scope="col">Ações</th>
                </tr>
              </thead>

              <tbody>
                {produtos.map((produto) => (
                  <tr key={produto.id}>
                    <td>
                      <strong>{produto.nome}</strong>
                      <span className="descricao">
                        {produto.descricao || "Sem descrição"}
                      </span>
                    </td>

                    <td>{nomeCategoria(produto.categoria)}</td>

                    <td className="numero">{moeda.format(produto.preco)}</td>

                    <td className="numero">
                      <span
                        className={
                          produto.quantidade === 0
                            ? "estoque esgotado"
                            : "estoque"
                        }
                      >
                        {produto.quantidade}
                      </span>
                    </td>

                    <td>
                      <div className="acoes">
                        <button
                          type="button"
                          disabled={ocupado}
                          aria-label={`Editar ${produto.nome}`}
                          onClick={() => abrirEdicao(produto)}
                        >
                          Editar
                        </button>

                        <button
                          type="button"
                          className="botao-excluir"
                          disabled={ocupado}
                          aria-label={`Excluir ${produto.nome}`}
                          onClick={() => void excluirProduto(produto)}
                        >
                          {excluindoId === produto.id
                            ? "Excluindo..."
                            : "Excluir"}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </main>
  );
}
