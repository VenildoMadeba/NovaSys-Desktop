package mz.novasys.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;

public class RelatoriosController {

    @FXML private DatePicker dpDataInicio;
    @FXML private DatePicker dpDataFim;

    @FXML private Label lblTotalFaturado;
    @FXML private Label lblTotalDescontos;
    @FXML private Label lblLucroEstimado;

    // Tabela Vendas
    @FXML private TableView<VendaRowViewModel> tabelaVendas;
    @FXML private TableColumn<VendaRowViewModel, Integer> colId;
    @FXML private TableColumn<VendaRowViewModel, String> colData;
    @FXML private TableColumn<VendaRowViewModel, String> colCliente;
    @FXML private TableColumn<VendaRowViewModel, String> colPagamento;
    @FXML private TableColumn<VendaRowViewModel, Double> colTotalLiquido;

    // Tabela Itens
    @FXML private Label lblItensVendaTitulo;
    @FXML private TableView<ItemRowViewModel> tabelaItensVenda;
    @FXML private TableColumn<ItemRowViewModel, String> colItemProduto;
    @FXML private TableColumn<ItemRowViewModel, Double> colItemQtd;
    @FXML private TableColumn<ItemRowViewModel, Double> colItemPreco;
    @FXML private TableColumn<ItemRowViewModel, Double> colItemSubtotal;

    private ObservableList<VendaRowViewModel> listaVendas = FXCollections.observableArrayList();
    private ObservableList<ItemRowViewModel> listaItens = FXCollections.observableArrayList();

    public static class VendaRowViewModel {
        private int id; private String data; private String cliente; private String pagamento; private double totalLiquido;
        public VendaRowViewModel(int id, String data, String cliente, String pagamento, double totalLiquido) {
            this.id = id; this.data = data; this.cliente = cliente; this.pagamento = pagamento; this.totalLiquido = totalLiquido;
        }
        public int getId() { return id; }
        public String getData() { return data; }
        public String getCliente() { return cliente; }
        public String getPagamento() { return pagamento; }
        public double getTotalLiquido() { return totalLiquido; }
    }

    public static class ItemRowViewModel {
        private String produto; private double quantidade; private double precoUnitario; private double subtotal;
        public ItemRowViewModel(String produto, double quantidade, double precoUnitario, double subtotal) {
            this.produto = produto; this.quantidade = quantidade; this.precoUnitario = precoUnitario; this.subtotal = subtotal;
        }
        public String getProduto() { return produto; }
        public double getQuantidade() { return quantidade; }
        public double getPrecoUnitario() { return precoUnitario; }
        public double getSubtotal() { return subtotal; }
    }

    @FXML
    public void initialize() {
        dpDataInicio.setValue(LocalDate.now().minusDays(30)); // Padrão: últimos 30 dias
        dpDataFim.setValue(LocalDate.now());

        configurarTabelas();
        carregarRelatorio();
    }

    private void configurarTabelas() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colData.setCellValueFactory(new PropertyValueFactory<>("data"));
        colCliente.setCellValueFactory(new PropertyValueFactory<>("cliente"));
        colPagamento.setCellValueFactory(new PropertyValueFactory<>("pagamento"));
        colTotalLiquido.setCellValueFactory(new PropertyValueFactory<>("totalLiquido"));
        tabelaVendas.setItems(listaVendas);

        colItemProduto.setCellValueFactory(new PropertyValueFactory<>("produto"));
        colItemQtd.setCellValueFactory(new PropertyValueFactory<>("quantidade"));
        colItemPreco.setCellValueFactory(new PropertyValueFactory<>("precoUnitario"));
        colItemSubtotal.setCellValueFactory(new PropertyValueFactory<>("subtotal"));
        tabelaItensVenda.setItems(listaItens);
    }

    @FXML
    public void filtrarRelatorio(ActionEvent event) {
        carregarRelatorio();
    }

    private void carregarRelatorio() {
        listaVendas.clear();
        listaItens.clear();
        lblItensVendaTitulo.setText("Itens da Venda (Selecione na tabela)");

        LocalDate dtInicio = dpDataInicio.getValue();
        LocalDate dtFim = dpDataFim.getValue();

        if (dtInicio == null || dtFim == null) return;

        String strInicio = dtInicio.toString();
        String strFim = dtFim.toString();

        double sumFaturado = 0.0;
        double sumDescontos = 0.0;
        double sumLucro = 0.0;

        // Uso da funcao DATE() do SQLite para compatibilidade total com timestamps
        String sqlVendas = "SELECT v.id, v.data_venda, v.total_liquido, v.desconto, v.forma_pagamento, c.nome AS cliente_nome " +
                "FROM vendas v LEFT JOIN clientes c ON v.cliente_id = c.id " +
                "WHERE DATE(v.data_venda) BETWEEN ? AND ? ORDER BY v.id DESC";

        try (Connection conn = Database.getConexao(); PreparedStatement st = conn.prepareStatement(sqlVendas)) {
            st.setString(1, strInicio);
            st.setString(2, strFim);
            ResultSet rs = st.executeQuery();

            while (rs.next()) {
                int idVenda = rs.getInt("id");
                double totLiquido = rs.getDouble("total_liquido");
                double desconto = rs.getDouble("desconto");
                String cliente = rs.getString("cliente_nome");
                if (cliente == null || cliente.isEmpty()) cliente = "Consumidor Final";

                sumFaturado += totLiquido;
                sumDescontos += desconto;

                listaVendas.add(new VendaRowViewModel(
                        idVenda, rs.getString("data_venda"), cliente, rs.getString("forma_pagamento"), totLiquido
                ));
            }

            // Calculo do Lucro Bruto Estimado
            String sqlLucro = "SELECT SUM((iv.preco_unitario - p.preco_custo) * iv.quantidade) AS lucro_total " +
                    "FROM itens_venda iv JOIN produtos p ON iv.produto_id = p.id " +
                    "JOIN vendas v ON iv.venda_id = v.id " +
                    "WHERE DATE(v.data_venda) BETWEEN ? AND ?";
            try (PreparedStatement stL = conn.prepareStatement(sqlLucro)) {
                stL.setString(1, strInicio);
                stL.setString(2, strFim);
                ResultSet rsL = stL.executeQuery();
                if (rsL.next()) {
                    sumLucro = rsL.getDouble("lucro_total");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        lblTotalFaturado.setText(String.format("%.2f MT", sumFaturado));
        lblTotalDescontos.setText(String.format("%.2f MT", sumDescontos));
        lblLucroEstimado.setText(String.format("%.2f MT", sumLucro));
    }

    @FXML
    public void selecionarVenda(MouseEvent event) {
        VendaRowViewModel vendaSel = tabelaVendas.getSelectionModel().getSelectedItem();
        if (vendaSel == null) return;

        lblItensVendaTitulo.setText("Itens da Venda #" + vendaSel.getId());
        listaItens.clear();

        String sqlItens = "SELECT p.nome, iv.quantidade, iv.preco_unitario, iv.subtotal " +
                "FROM itens_venda iv JOIN produtos p ON iv.produto_id = p.id " +
                "WHERE iv.venda_id = ?";

        try (Connection conn = Database.getConexao(); PreparedStatement st = conn.prepareStatement(sqlItens)) {
            st.setInt(1, vendaSel.getId());
            ResultSet rs = st.executeQuery();

            while (rs.next()) {
                listaItens.add(new ItemRowViewModel(
                        rs.getString("nome"), rs.getDouble("quantidade"), rs.getDouble("preco_unitario"), rs.getDouble("subtotal")
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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