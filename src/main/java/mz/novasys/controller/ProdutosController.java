package mz.novasys.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;
import mz.novasys.database.Database;
import mz.novasys.model.Produto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class ProdutosController {

    // Inputs Formulário CRUD
    @FXML private TextField txtNome;
    @FXML private TextField txtCodigoBarras;
    @FXML private TextField txtPrecoCusto;
    @FXML private TextField txtPrecoVenda;
    @FXML private TextField txtStockActual;
    @FXML private TextField txtStockMinimo;
    @FXML private Label lblMensagemCrud;

    // Tabela e Pesquisa
    @FXML private TextField txtPesquisa;
    @FXML private TableView<Produto> tabelaProdutos;
    @FXML private TableColumn<Produto, Integer> colId;
    @FXML private TableColumn<Produto, String> colNome;
    @FXML private TableColumn<Produto, String> colCodigo;
    @FXML private TableColumn<Produto, Double> colCusto;
    @FXML private TableColumn<Produto, Double> colVenda;
    @FXML private TableColumn<Produto, Double> colStock;
    @FXML private TableColumn<Produto, Double> colStockMin;

    private ObservableList<Produto> masterDataProdutos = FXCollections.observableArrayList();
    private FilteredList<Produto> filteredProdutos;
    private Produto produtoSelecionado = null;

    @FXML
    public void initialize() {
        configurarTabela();
        carregarDadosBaseDados();

        // Implementação do Live Search
        filteredProdutos = new FilteredList<>(masterDataProdutos, p -> true);
        tabelaProdutos.setItems(filteredProdutos);
        txtPesquisa.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredProdutos.setPredicate(produto -> {
                if (newValue == null || newValue.isEmpty()) return true;
                String lowerCaseFilter = newValue.toLowerCase();
                if (produto.getNome().toLowerCase().contains(lowerCaseFilter)) return true;
                if (produto.getCodigoBarras() != null && produto.getCodigoBarras().toLowerCase().contains(lowerCaseFilter)) return true;
                return false;
            });
        });
    }

    private void configurarTabela() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colNome.setCellValueFactory(new PropertyValueFactory<>("nome"));
        colCodigo.setCellValueFactory(new PropertyValueFactory<>("codigoBarras"));
        colCusto.setCellValueFactory(new PropertyValueFactory<>("precoCusto"));
        colVenda.setCellValueFactory(new PropertyValueFactory<>("precoVenda"));
        colStock.setCellValueFactory(new PropertyValueFactory<>("stockActual"));
        colStockMin.setCellValueFactory(new PropertyValueFactory<>("stockMinimo"));
    }

    private void carregarDadosBaseDados() {
        masterDataProdutos.clear();
        try (Connection conn = Database.getConexao(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT * FROM produtos ORDER BY nome ASC");
            while (rs.next()) {
                Produto p = new Produto();
                p.setId(rs.getInt("id"));
                p.setNome(rs.getString("nome"));
                p.setCodigoBarras(rs.getString("codigo_barras"));
                p.setPrecoCusto(rs.getDouble("preco_custo"));
                p.setPrecoVenda(rs.getDouble("preco_venda"));
                p.setStockActual(rs.getDouble("stock_actual"));
                p.setStockMinimo(rs.getDouble("stock_minimo"));
                masterDataProdutos.add(p);
            }
        } catch (Exception e) {
            mostrarMensagemCrud("Erro ao ler base de dados: " + e.getMessage(), true);
        }
    }

    @FXML
    public void selecionarProduto(MouseEvent event) {
        produtoSelecionado = tabelaProdutos.getSelectionModel().getSelectedItem();
        if (produtoSelecionado != null) {
            txtNome.setText(produtoSelecionado.getNome());
            txtCodigoBarras.setText(produtoSelecionado.getCodigoBarras() == null ? "" : produtoSelecionado.getCodigoBarras());
            txtPrecoCusto.setText(String.valueOf(produtoSelecionado.getPrecoCusto()));
            txtPrecoVenda.setText(String.valueOf(produtoSelecionado.getPrecoVenda()));
            txtStockActual.setText(String.valueOf(produtoSelecionado.getStockActual()));
            txtStockMinimo.setText(String.valueOf(produtoSelecionado.getStockMinimo()));
            lblMensagemCrud.setText("");
        }
    }

    private double parseDoubleSafe(String val, double defaultValue) {
        if (val == null || val.trim().isEmpty()) return defaultValue;
        try { return Double.parseDouble(val.trim().replace(",", ".")); } 
        catch (NumberFormatException e) { return defaultValue; }
    }

    @FXML
    public void salvarProduto(ActionEvent event) {
        String nome = txtNome.getText().trim();
        String codigoBarras = txtCodigoBarras.getText().trim();
        if (codigoBarras.isEmpty()) codigoBarras = null;

        if (nome.isEmpty()) {
            mostrarMensagemCrud("O nome do artigo é obrigatório!", true);
            return;
        }

        double precoCusto = parseDoubleSafe(txtPrecoCusto.getText(), 0.0);
        double precoVenda = parseDoubleSafe(txtPrecoVenda.getText(), 0.0);
        double stockActual = parseDoubleSafe(txtStockActual.getText(), 0.0);
        double stockMinimo = parseDoubleSafe(txtStockMinimo.getText(), 5.0);

        if (precoVenda <= 0) {
            mostrarMensagemCrud("O preco de venda deve ser maior que zero!", true);
            return;
        }

        try (Connection conn = Database.getConexao()) {
            if (produtoSelecionado == null) {
                // CREATE
                String sql = "INSERT INTO produtos (nome, codigo_barras, preco_custo, preco_venda, stock_actual, stock_minimo) VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement st = conn.prepareStatement(sql)) {
                    st.setString(1, nome);
                    st.setString(2, codigoBarras);
                    st.setDouble(3, precoCusto);
                    st.setDouble(4, precoVenda);
                    st.setDouble(5, stockActual);
                    st.setDouble(6, stockMinimo);
                    st.executeUpdate();
                    mostrarMensagemCrud("Produto registado com sucesso!", false);
                }
            } else {
                // UPDATE
                String sql = "UPDATE produtos SET nome=?, codigo_barras=?, preco_custo=?, preco_venda=?, stock_actual=?, stock_minimo=? WHERE id=?";
                try (PreparedStatement st = conn.prepareStatement(sql)) {
                    st.setString(1, nome);
                    st.setString(2, codigoBarras);
                    st.setDouble(3, precoCusto);
                    st.setDouble(4, precoVenda);
                    st.setDouble(5, stockActual);
                    st.setDouble(6, stockMinimo);
                    st.setInt(7, produtoSelecionado.getId());
                    st.executeUpdate();
                    mostrarMensagemCrud("Produto atualizado com sucesso!", false);
                }
            }
            limparFormulario(null);
            carregarDadosBaseDados();
        } catch (Exception e) {
            if (e.getMessage().contains("UNIQUE constraint failed")) {
                mostrarMensagemCrud("Erro: Este codigo de barras ja esta em uso!", true);
            } else {
                mostrarMensagemCrud("Erro ao salvar: " + e.getMessage(), true);
            }
        }
    }

    @FXML
    public void apagarProduto(ActionEvent event) {
        if (produtoSelecionado == null) {
            mostrarMensagemCrud("Selecione um produto na tabela para apagar.", true);
            return;
        }

        try (Connection conn = Database.getConexao()) {
            // GUARD CLAUSE: Verificar histórico em itens_venda
            String sqlCheck = "SELECT COUNT(*) AS total FROM itens_venda WHERE produto_id = ?";
            try (PreparedStatement stCheck = conn.prepareStatement(sqlCheck)) {
                stCheck.setInt(1, produtoSelecionado.getId());
                ResultSet rs = stCheck.executeQuery();
                if (rs.next() && rs.getInt("total") > 0) {
                    mostrarMensagemCrud("Operacao bloqueada: O produto ja foi vendido e nao pode ser apagado.", true);
                    return;
                }
            }

            String sqlDelete = "DELETE FROM produtos WHERE id = ?";
            try (PreparedStatement st = conn.prepareStatement(sqlDelete)) {
                st.setInt(1, produtoSelecionado.getId());
                st.executeUpdate();
                mostrarMensagemCrud("Produto apagado permanentemente.", false);
                limparFormulario(null);
                carregarDadosBaseDados();
            }
        } catch (Exception e) {
            mostrarMensagemCrud("Erro ao apagar: " + e.getMessage(), true);
        }
    }

    @FXML
    public void limparFormulario(ActionEvent event) {
        produtoSelecionado = null;
        txtNome.clear();
        txtCodigoBarras.clear();
        txtPrecoCusto.clear();
        txtPrecoVenda.clear();
        txtStockActual.clear();
        txtStockMinimo.clear();
        lblMensagemCrud.setText("");
        tabelaProdutos.getSelectionModel().clearSelection();
    }

    private void mostrarMensagemCrud(String msg, boolean erro) {
        lblMensagemCrud.setText(msg);
        lblMensagemCrud.setStyle(erro ? "-fx-text-fill: #dc2626;" : "-fx-text-fill: #16a34a;");
    }

    @FXML
    public void aoClicarVoltar(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/dashboard.fxml"));
            Scene scene = ((Node) event.getSource()).getScene();
            scene.setRoot(root);
        } catch (Exception e) { e.printStackTrace(); }
    }
}