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
import mz.novasys.model.Cliente;
import mz.novasys.model.ItemVenda;
import mz.novasys.model.Produto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;

public class VendaController {

    // Painel Esquerdo (Pesquisa)
    @FXML private TextField txtPesquisaProduto;
    @FXML private TableView<Produto> tabelaProdutosPesquisa;
    @FXML private TableColumn<Produto, String> colBuscaNome;
    @FXML private TableColumn<Produto, Double> colBuscaPreco;
    @FXML private TableColumn<Produto, Double> colBuscaStock;
    @FXML private TextField txtQuantidade;

    // Painel Central (Carrinho)
    @FXML private TableView<ItemVenda> tabelaCarrinho;
    @FXML private TableColumn<ItemVenda, String> colCarNome;
    @FXML private TableColumn<ItemVenda, Double> colCarQtd;
    @FXML private TableColumn<ItemVenda, Double> colCarPreco;
    @FXML private TableColumn<ItemVenda, Double> colCarSubtotal;

    // Painel Direito (Resumo e Pagamento)
    @FXML private Label lblTotalBruto;
    @FXML private TextField txtDesconto;
    @FXML private Label lblTotalLiquido;
    @FXML private ComboBox<String> cmbFormaPagamento;
    @FXML private ComboBox<Cliente> cmbCliente;
    @FXML private TextField txtValorPago;
    @FXML private Label lblTroco;
    @FXML private Label lblMensagem;

    private ObservableList<Produto> masterDataProdutos = FXCollections.observableArrayList();
    private FilteredList<Produto> filteredProdutos;
    private ObservableList<ItemVenda> itensCarrinho = FXCollections.observableArrayList();
    private ObservableList<Cliente> listaClientes = FXCollections.observableArrayList();

    private double totalBruto = 0.0;
    private double totalLiquido = 0.0;

    @FXML
    public void initialize() {
        configurarTabelas();
        carregarDadosBaseDados();

        // Configurar Pesquisa em Tempo Real
        filteredProdutos = new FilteredList<>(masterDataProdutos, p -> true);
        tabelaProdutosPesquisa.setItems(filteredProdutos);
        txtPesquisaProduto.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredProdutos.setPredicate(produto -> {
                if (newValue == null || newValue.isEmpty()) return true;
                String lowerCaseFilter = newValue.toLowerCase();
                if (produto.getNome().toLowerCase().contains(lowerCaseFilter)) return true;
                if (produto.getCodigoBarras() != null && produto.getCodigoBarras().contains(lowerCaseFilter)) return true;
                return false;
            });
        });

        // Configurar ComboBox de Pagamento
        cmbFormaPagamento.setItems(FXCollections.observableArrayList("DINHEIRO", "MPESA", "EMOLA", "POS / CARTAO", "A CREDITO (FIADO)"));
        cmbFormaPagamento.getSelectionModel().selectFirst();
        
        // Listeners para Atualizacao de Calculos (Desconto e Troco)
        txtDesconto.textProperty().addListener((obs, oldVal, newVal) -> calcularTotais());
        txtValorPago.textProperty().addListener((obs, oldVal, newVal) -> calcularTroco());
    }

    private void configurarTabelas() {
        colBuscaNome.setCellValueFactory(new PropertyValueFactory<>("nome"));
        colBuscaPreco.setCellValueFactory(new PropertyValueFactory<>("precoVenda"));
        colBuscaStock.setCellValueFactory(new PropertyValueFactory<>("stockActual"));

        colCarNome.setCellValueFactory(new PropertyValueFactory<>("nomeProduto"));
        colCarQtd.setCellValueFactory(new PropertyValueFactory<>("quantidade"));
        colCarPreco.setCellValueFactory(new PropertyValueFactory<>("precoUnitario"));
        colCarSubtotal.setCellValueFactory(new PropertyValueFactory<>("subtotal"));
        tabelaCarrinho.setItems(itensCarrinho);
    }

    private void carregarDadosBaseDados() {
        masterDataProdutos.clear();
        listaClientes.clear();
        try (Connection conn = Database.getConexao(); Statement stmt = conn.createStatement()) {
            
            // Carrega produtos com stock > 0
            ResultSet rsP = stmt.executeQuery("SELECT * FROM produtos WHERE stock_actual > 0 ORDER BY nome ASC");
            while (rsP.next()) {
                Produto p = new Produto();
                p.setId(rsP.getInt("id"));
                p.setNome(rsP.getString("nome"));
                p.setCodigoBarras(rsP.getString("codigo_barras"));
                p.setPrecoVenda(rsP.getDouble("preco_venda"));
                p.setStockActual(rsP.getDouble("stock_actual"));
                masterDataProdutos.add(p);
            }

            // Carrega Clientes
            ResultSet rsC = stmt.executeQuery("SELECT * FROM clientes ORDER BY nome ASC");
            while (rsC.next()) {
                Cliente c = new Cliente();
                c.setId(rsC.getInt("id"));
                c.setNome(rsC.getString("nome"));
                c.setSaldoDevedor(rsC.getDouble("saldo_devedor"));
                listaClientes.add(c);
            }
        } catch (Exception e) {
            lblMensagem.setText("Erro ao ler base de dados.");
        }
        cmbCliente.setItems(listaClientes);
    }

    @FXML
    public void adicionarItem(ActionEvent event) {
        Produto p = tabelaProdutosPesquisa.getSelectionModel().getSelectedItem();
        if (p == null) {
            lblMensagem.setText("Seleccione um produto na lista de pesquisa.");
            return;
        }

        double qtd = 1.0;
        try {
            qtd = Double.parseDouble(txtQuantidade.getText().trim().replace(",", "."));
            if (qtd <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            lblMensagem.setText("Quantidade invalida.");
            return;
        }

        if (qtd > p.getStockActual()) {
            lblMensagem.setText("Stock insuficiente! Apenas " + p.getStockActual() + " disponiveis.");
            return;
        }

        itensCarrinho.add(new ItemVenda(p, qtd));
        calcularTotais();
        txtQuantidade.setText("1");
        lblMensagem.setText(""); // Limpa mensagem de erro
    }

    @FXML
    public void removerItem(ActionEvent event) {
        ItemVenda sel = tabelaCarrinho.getSelectionModel().getSelectedItem();
        if (sel != null) {
            itensCarrinho.remove(sel);
            calcularTotais();
        }
    }

    @FXML
    public void cancelarVenda(ActionEvent event) {
        itensCarrinho.clear();
        txtDesconto.clear();
        txtValorPago.clear();
        calcularTotais();
    }

    private void calcularTotais() {
        totalBruto = 0.0;
        for (ItemVenda item : itensCarrinho) {
            totalBruto += item.getSubtotal();
        }

        double desconto = 0.0;
        try {
            if (!txtDesconto.getText().trim().isEmpty()) {
                desconto = Double.parseDouble(txtDesconto.getText().trim().replace(",", "."));
            }
        } catch (NumberFormatException ignored) {}

        totalLiquido = Math.max(0.0, totalBruto - desconto);

        lblTotalBruto.setText(String.format("%.2f", totalBruto));
        lblTotalLiquido.setText(String.format("%.2f", totalLiquido));
        calcularTroco();
    }

    private void calcularTroco() {
        double pago = 0.0;
        try {
            if (!txtValorPago.getText().trim().isEmpty()) {
                pago = Double.parseDouble(txtValorPago.getText().trim().replace(",", "."));
            }
        } catch (NumberFormatException ignored) {}

        double troco = Math.max(0.0, pago - totalLiquido);
        lblTroco.setText(String.format("%.2f", troco));
    }

    @FXML
    public void finalizarVenda(ActionEvent event) {
        if (itensCarrinho.isEmpty()) {
            lblMensagem.setText("O carrinho esta vazio.");
            return;
        }

        String formaPag = cmbFormaPagamento.getValue();
        Cliente cliente = cmbCliente.getValue();

        // VALIDACAO REGRA DE NEGOCIO: Venda a Fiado exige Cliente
        if ("A CREDITO (FIADO)".equals(formaPag) && cliente == null) {
            lblMensagem.setText("Para FIADO, tem de selecionar um Cliente.");
            return;
        }

        double valorPago = 0.0;
        try {
            if (!txtValorPago.getText().trim().isEmpty()) {
                valorPago = Double.parseDouble(txtValorPago.getText().trim().replace(",", "."));
            }
        } catch (NumberFormatException ignored) {}

        // Se não for fiado, tem de pagar o valor completo
        if (!"A CREDITO (FIADO)".equals(formaPag) && valorPago < totalLiquido) {
            lblMensagem.setText("Valor pago insuficiente.");
            return;
        }

        double troco = Math.max(0.0, valorPago - totalLiquido);
        double desconto = totalBruto - totalLiquido;

        Connection conn = null;
        try {
            conn = Database.getConexao();
            conn.setAutoCommit(false); // Inicia Transacao

            // 1. Gravar Venda Master
            String sqlVenda = "INSERT INTO vendas (cliente_id, data_venda, total_bruto, desconto, total_liquido, forma_pagamento, valor_pago, troco) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            int idVenda = -1;
            try (PreparedStatement st = conn.prepareStatement(sqlVenda, Statement.RETURN_GENERATED_KEYS)) {
                if (cliente != null) st.setInt(1, cliente.getId()); else st.setNull(1, java.sql.Types.INTEGER);
                st.setString(2, LocalDateTime.now().toString());
                st.setDouble(3, totalBruto);
                st.setDouble(4, desconto);
                st.setDouble(5, totalLiquido);
                st.setString(6, formaPag);
                st.setDouble(7, valorPago);
                st.setDouble(8, troco);
                st.executeUpdate();
                ResultSet rs = st.getGeneratedKeys();
                if (rs.next()) idVenda = rs.getInt(1);
            }

            // 2. Gravar Itens e Abater Stock
            String sqlItem = "INSERT INTO itens_venda (venda_id, produto_id, quantidade, preco_unitario, subtotal) VALUES (?, ?, ?, ?, ?)";
            String sqlStock = "UPDATE produtos SET stock_actual = stock_actual - ? WHERE id = ?";
            try (PreparedStatement stItem = conn.prepareStatement(sqlItem);
                 PreparedStatement stStock = conn.prepareStatement(sqlStock)) {
                for (ItemVenda item : itensCarrinho) {
                    // Gravar Item
                    stItem.setInt(1, idVenda);
                    stItem.setInt(2, item.getProduto().getId());
                    stItem.setDouble(3, item.getQuantidade());
                    stItem.setDouble(4, item.getPrecoUnitario());
                    stItem.setDouble(5, item.getSubtotal());
                    stItem.executeUpdate();
                    // Abater Stock
                    stStock.setDouble(1, item.getQuantidade());
                    stStock.setInt(2, item.getProduto().getId());
                    stStock.executeUpdate();
                }
            }

            // 3. Lançar Divida (Fiado)
            if ("A CREDITO (FIADO)".equals(formaPag) && cliente != null) {
                try (PreparedStatement stFiado = conn.prepareStatement("UPDATE clientes SET saldo_devedor = saldo_devedor + ? WHERE id = ?")) {
                    stFiado.setDouble(1, totalLiquido);
                    stFiado.setInt(2, cliente.getId());
                    stFiado.executeUpdate();
                }
            }

                        // 4. Lancamento Automático na Sessao de Caixa Aberta
            int sessaoId = -1;
            try (Statement stCheck = conn.createStatement();
                 ResultSet rsCheck = stCheck.executeQuery("SELECT id FROM sessoes_caixa WHERE status = 'ABERTO' ORDER BY id DESC LIMIT 1")) {
                if (rsCheck.next()) {
                    sessaoId = rsCheck.getInt("id");
                }
            }

            // Se nao houver caixa aberto, abre um automaticamente para nao travar a venda
            if (sessaoId == -1) {
                String sqlAutoAbrir = "INSERT INTO sessoes_caixa (operador, data_abertura, saldo_inicial, status) VALUES (?, ?, 0.0, 'ABERTO')";
                try (PreparedStatement stAuto = conn.prepareStatement(sqlAutoAbrir, Statement.RETURN_GENERATED_KEYS)) {
                    stAuto.setString(1, mz.novasys.util.Sessao.utilizadorLogado);
                    stAuto.setString(2, java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    stAuto.executeUpdate();
                    ResultSet rsAuto = stAuto.getGeneratedKeys();
                    if (rsAuto.next()) sessaoId = rsAuto.getInt(1);
                }
            }

            // Gravar Movimentacao no Caixa
            String sqlMovCaixa = "INSERT INTO movimentos_caixa (sessao_id, data_hora, tipo, descricao, valor, forma_pagamento) VALUES (?, ?, 'VENDA', ?, ?, ?)";
            try (PreparedStatement stMov = conn.prepareStatement(sqlMovCaixa)) {
                stMov.setInt(1, sessaoId);
                stMov.setString(2, java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                stMov.setString(3, "Venda #" + idVenda);
                stMov.setDouble(4, totalLiquido);
                stMov.setString(5, formaPag);
                stMov.executeUpdate();
            }

            conn.commit(); // Fecha Transacao
            
            // Sucesso!
            lblMensagem.setText("");
            lblMensagem.setStyle("-fx-text-fill: #16a34a;"); // Verde
            lblMensagem.setText("Venda efetuada com sucesso!");
            
            // Reset do ecra
            itensCarrinho.clear();
            txtDesconto.clear();
            txtValorPago.clear();
            calcularTotais();
            carregarDadosBaseDados(); // Recarrega stock e clientes atualizados

        } catch (Exception e) {
            if (conn != null) { try { conn.rollback(); } catch (Exception ignored) {} }
            lblMensagem.setText("Erro ao salvar: " + e.getMessage());
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