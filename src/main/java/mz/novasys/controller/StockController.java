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
import mz.novasys.database.Database;
import mz.novasys.model.Produto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class StockController {

    @FXML private ComboBox<Produto> cmbProdutos;
    @FXML private ComboBox<String> cmbTipoOperacao;
    @FXML private TextField txtQuantidade;
    @FXML private TextField txtObservacao;
    @FXML private Label lblMensagem;

    @FXML private TextField txtPesquisa;
    @FXML private TableView<StockRowViewModel> tabelaStock;
    @FXML private TableColumn<StockRowViewModel, String> colNome;
    @FXML private TableColumn<StockRowViewModel, String> colCodigo;
    @FXML private TableColumn<StockRowViewModel, Double> colStockActual;
    @FXML private TableColumn<StockRowViewModel, Double> colStockMinimo;
    @FXML private TableColumn<StockRowViewModel, String> colEstado;

    private ObservableList<Produto> listaProdutosCombo = FXCollections.observableArrayList();
    private ObservableList<StockRowViewModel> masterDataStock = FXCollections.observableArrayList();
    private FilteredList<StockRowViewModel> filteredStock;

    public static class StockRowViewModel {
        private int id; private String nome; private String codigo; private double stockActual; private double stockMinimo; private String estado;
        public StockRowViewModel(int id, String nome, String codigo, double stockActual, double stockMinimo, String estado) {
            this.id = id; this.nome = nome; this.codigo = codigo; this.stockActual = stockActual; this.stockMinimo = stockMinimo; this.estado = estado;
        }
        public int getId() { return id; }
        public String getNome() { return nome; }
        public String getCodigo() { return codigo; }
        public double getStockActual() { return stockActual; }
        public double getStockMinimo() { return stockMinimo; }
        public String getEstado() { return estado; }
    }

    @FXML
    public void initialize() {
        cmbTipoOperacao.setItems(FXCollections.observableArrayList(
                "ENTRADA (Reposicao/Compra)", 
                "SAIDA (Avaria/Validade)", 
                "AJUSTE (Perda/Quebra)"
        ));
        cmbTipoOperacao.getSelectionModel().selectFirst();

        configurarTabela();
        carregarDados();

        // Live Search na tabela de stock
        filteredStock = new FilteredList<>(masterDataStock, s -> true);
        tabelaStock.setItems(filteredStock);
        txtPesquisa.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredStock.setPredicate(item -> {
                if (newVal == null || newVal.isEmpty()) return true;
                String filter = newVal.toLowerCase();
                if (item.getNome().toLowerCase().contains(filter)) return true;
                if (item.getCodigo() != null && item.getCodigo().toLowerCase().contains(filter)) return true;
                return false;
            });
        });
    }

    private void configurarTabela() {
        colNome.setCellValueFactory(new PropertyValueFactory<>("nome"));
        colCodigo.setCellValueFactory(new PropertyValueFactory<>("codigo"));
        colStockActual.setCellValueFactory(new PropertyValueFactory<>("stockActual"));
        colStockMinimo.setCellValueFactory(new PropertyValueFactory<>("stockMinimo"));
        colEstado.setCellValueFactory(new PropertyValueFactory<>("estado"));
    }

    private void carregarDados() {
        masterDataStock.clear();
        listaProdutosCombo.clear();
        try (Connection conn = Database.getConexao(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT * FROM produtos ORDER BY nome ASC");
            while (rs.next()) {
                Produto p = new Produto();
                p.setId(rs.getInt("id"));
                p.setNome(rs.getString("nome"));
                p.setCodigoBarras(rs.getString("codigo_barras"));
                p.setStockActual(rs.getDouble("stock_actual"));
                p.setStockMinimo(rs.getDouble("stock_minimo"));
                listaProdutosCombo.add(p);

                // Calcular Estado Visual do Inventário
                String estado = "NORMAL OK";
                if (p.getStockActual() <= 0) {
                    estado = "CRITICO (ESGOTADO)";
                } else if (p.getStockActual() <= p.getStockMinimo()) {
                    estado = "ALERTA (STOCK MINIMO)";
                }

                masterDataStock.add(new StockRowViewModel(
                        p.getId(), p.getNome(), p.getCodigoBarras(), p.getStockActual(), p.getStockMinimo(), estado
                ));
            }
        } catch (Exception e) {
            mostrarMensagem("Erro ao carregar stock: " + e.getMessage(), true);
        }
        cmbProdutos.setItems(listaProdutosCombo);
    }

    @FXML
    public void confirmarAjuste(ActionEvent event) {
        Produto produtoSelected = cmbProdutos.getValue();
        if (produtoSelected == null) {
            mostrarMensagem("Seleccione um produto para ajustar!", true);
            return;
        }

        double qtd = 0.0;
        try {
            qtd = Double.parseDouble(txtQuantidade.getText().trim().replace(",", "."));
            if (qtd <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            mostrarMensagem("Informe uma quantidade numerica valida superior a zero!", true);
            return;
        }

        String operacao = cmbTipoOperacao.getValue();
        boolean isEntrada = operacao.startsWith("ENTRADA");

        // Se for saída e a quantidade exceder o stock atual
        if (!isEntrada && qtd > produtoSelected.getStockActual()) {
            mostrarMensagem("Quantidade de baixa excede o stock disponivel (" + produtoSelected.getStockActual() + ")!", true);
            return;
        }

        double novoStock = isEntrada ? (produtoSelected.getStockActual() + qtd) : (produtoSelected.getStockActual() - qtd);

        try (Connection conn = Database.getConexao()) {
            String sql = "UPDATE produtos SET stock_actual = ? WHERE id = ?";
            try (PreparedStatement st = conn.prepareStatement(sql)) {
                st.setDouble(1, novoStock);
                st.setInt(2, produtoSelected.getId());
                st.executeUpdate();

                mostrarMensagem("Stock atualizado com sucesso! Novo saldo: " + novoStock, false);
                txtQuantidade.clear();
                txtObservacao.clear();
                cmbProdutos.getSelectionModel().clearSelection();
                carregarDados();
            }
        } catch (Exception e) {
            mostrarMensagem("Erro ao atualizar stock: " + e.getMessage(), true);
        }
    }

    private void mostrarMensagem(String msg, boolean erro) {
        lblMensagem.setText(msg);
        lblMensagem.setStyle(erro ? "-fx-text-fill: #dc2626;" : "-fx-text-fill: #16a34a;");
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