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
import mz.novasys.model.Cliente;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class ClientesController {

    // Inputs Formulário CRUD
    @FXML private TextField txtNome;
    @FXML private TextField txtTelefone;
    @FXML private TextField txtNuit;
    @FXML private Label lblMensagemCrud;

    // Inputs Liquidação de Dívida
    @FXML private Label lblNomeClienteDivida;
    @FXML private Label lblSaldoDevedor;
    @FXML private TextField txtValorLiquidar;
    @FXML private Label lblMensagemDivida;

    // Tabela e Pesquisa
    @FXML private TextField txtPesquisa;
    @FXML private TableView<Cliente> tabelaClientes;
    @FXML private TableColumn<Cliente, Integer> colId;
    @FXML private TableColumn<Cliente, String> colNome;
    @FXML private TableColumn<Cliente, String> colTelefone;
    @FXML private TableColumn<Cliente, String> colNuit;
    @FXML private TableColumn<Cliente, Double> colSaldo;

    private ObservableList<Cliente> masterDataClientes = FXCollections.observableArrayList();
    private FilteredList<Cliente> filteredClientes;
    private Cliente clienteSelecionado = null;

    @FXML
    public void initialize() {
        configurarTabela();
        carregarDadosBaseDados();

        // Implementação do Live Search (Pesquisa em Tempo Real)
        filteredClientes = new FilteredList<>(masterDataClientes, c -> true);
        tabelaClientes.setItems(filteredClientes);
        txtPesquisa.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredClientes.setPredicate(cliente -> {
                if (newValue == null || newValue.isEmpty()) return true;
                String lowerCaseFilter = newValue.toLowerCase();
                if (cliente.getNome().toLowerCase().contains(lowerCaseFilter)) return true;
                if (cliente.getNuit() != null && cliente.getNuit().contains(lowerCaseFilter)) return true;
                return false;
            });
        });
    }

    private void configurarTabela() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colNome.setCellValueFactory(new PropertyValueFactory<>("nome"));
        colTelefone.setCellValueFactory(new PropertyValueFactory<>("telefone"));
        colNuit.setCellValueFactory(new PropertyValueFactory<>("nuit"));
        colSaldo.setCellValueFactory(new PropertyValueFactory<>("saldoDevedor"));
    }

    private void carregarDadosBaseDados() {
        masterDataClientes.clear();
        try (Connection conn = Database.getConexao(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT * FROM clientes ORDER BY nome ASC");
            while (rs.next()) {
                Cliente c = new Cliente();
                c.setId(rs.getInt("id"));
                c.setNome(rs.getString("nome"));
                c.setTelefone(rs.getString("telefone"));
                c.setNuit(rs.getString("nuit"));
                c.setSaldoDevedor(rs.getDouble("saldo_devedor"));
                masterDataClientes.add(c);
            }
        } catch (Exception e) {
            mostrarMensagemCrud("Erro ao ler base de dados: " + e.getMessage(), true);
        }
    }

    @FXML
    public void selecionarCliente(MouseEvent event) {
        clienteSelecionado = tabelaClientes.getSelectionModel().getSelectedItem();
        if (clienteSelecionado != null) {
            // Preencher CRUD
            txtNome.setText(clienteSelecionado.getNome());
            txtTelefone.setText(clienteSelecionado.getTelefone());
            txtNuit.setText(clienteSelecionado.getNuit());

            // Preencher Painel de Dívida
            lblNomeClienteDivida.setText(clienteSelecionado.getNome());
            lblSaldoDevedor.setText(String.format("%.2f MT", clienteSelecionado.getSaldoDevedor()));
            txtValorLiquidar.clear();
            lblMensagemCrud.setText("");
            lblMensagemDivida.setText("");
        }
    }

    @FXML
    public void salvarCliente(ActionEvent event) {
        String nome = txtNome.getText().trim();
        String telefone = txtTelefone.getText().trim();
        String nuit = txtNuit.getText().trim();

        if (nome.isEmpty()) {
            mostrarMensagemCrud("O nome é obrigatório!", true);
            return;
        }

        try (Connection conn = Database.getConexao()) {
            if (clienteSelecionado == null) {
                // CREATE - Regra: Novos clientes nascem com saldo 0.0
                String sql = "INSERT INTO clientes (nome, telefone, nuit, saldo_devedor) VALUES (?, ?, ?, 0.0)";
                try (PreparedStatement st = conn.prepareStatement(sql)) {
                    st.setString(1, nome);
                    st.setString(2, telefone);
                    st.setString(3, nuit);
                    st.executeUpdate();
                    mostrarMensagemCrud("Cliente registado com sucesso!", false);
                }
            } else {
                // UPDATE - Regra: Saldo devedor não é alterado aqui.
                String sql = "UPDATE clientes SET nome = ?, telefone = ?, nuit = ? WHERE id = ?";
                try (PreparedStatement st = conn.prepareStatement(sql)) {
                    st.setString(1, nome);
                    st.setString(2, telefone);
                    st.setString(3, nuit);
                    st.setInt(4, clienteSelecionado.getId());
                    st.executeUpdate();
                    mostrarMensagemCrud("Cliente atualizado com sucesso!", false);
                }
            }
            limparFormulario(null);
            carregarDadosBaseDados();
        } catch (Exception e) {
            mostrarMensagemCrud("Erro ao salvar: " + e.getMessage(), true);
        }
    }

    @FXML
    public void apagarCliente(ActionEvent event) {
        if (clienteSelecionado == null) {
            mostrarMensagemCrud("Selecione um cliente na tabela para apagar.", true);
            return;
        }

        // GUARD CLAUSE 1: Não apagar clientes com dívidas.
        if (clienteSelecionado.getSaldoDevedor() > 0) {
            mostrarMensagemCrud("Operacao bloqueada: O cliente possui dividas ativas.", true);
            return;
        }

        try (Connection conn = Database.getConexao()) {
            // GUARD CLAUSE 2: Integridade referencial (Verificar histórico de vendas)
            String sqlCheck = "SELECT COUNT(*) AS total FROM vendas WHERE cliente_id = ?";
            try (PreparedStatement stCheck = conn.prepareStatement(sqlCheck)) {
                stCheck.setInt(1, clienteSelecionado.getId());
                ResultSet rs = stCheck.executeQuery();
                if (rs.next() && rs.getInt("total") > 0) {
                    mostrarMensagemCrud("Operacao bloqueada: Cliente possui historico de vendas associado.", true);
                    return;
                }
            }

            // Se passar nas verificações, executar o DELETE.
            String sqlDelete = "DELETE FROM clientes WHERE id = ?";
            try (PreparedStatement st = conn.prepareStatement(sqlDelete)) {
                st.setInt(1, clienteSelecionado.getId());
                st.executeUpdate();
                mostrarMensagemCrud("Cliente apagado permanentemente.", false);
                limparFormulario(null);
                carregarDadosBaseDados();
            }
        } catch (Exception e) {
            mostrarMensagemCrud("Erro ao apagar: " + e.getMessage(), true);
        }
    }

    @FXML
    public void liquidarDivida(ActionEvent event) {
        if (clienteSelecionado == null) {
            mostrarMensagemDivida("Selecione um cliente com saldo devedor.", true);
            return;
        }

        if (clienteSelecionado.getSaldoDevedor() <= 0) {
            mostrarMensagemDivida("Este cliente nao possui dividas ativas.", true);
            return;
        }

        double valorPagamento = 0.0;
        try {
            valorPagamento = Double.parseDouble(txtValorLiquidar.getText().trim().replace(",", "."));
            if (valorPagamento <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            mostrarMensagemDivida("Insira um valor numerico valido superior a zero.", true);
            return;
        }

        if (valorPagamento > clienteSelecionado.getSaldoDevedor()) {
            mostrarMensagemDivida("Valor excede a divida atual (MT " + clienteSelecionado.getSaldoDevedor() + ").", true);
            return;
        }

        try (Connection conn = Database.getConexao()) {
            String sql = "UPDATE clientes SET saldo_devedor = saldo_devedor - ? WHERE id = ?";
            try (PreparedStatement st = conn.prepareStatement(sql)) {
                st.setDouble(1, valorPagamento);
                st.setInt(2, clienteSelecionado.getId());
                st.executeUpdate();
                
                mostrarMensagemDivida("Divida amortizada com sucesso!", false);
                carregarDadosBaseDados(); // Atualiza memória
                limparFormulario(null); // Deseleciona para atualizar view
            }
        } catch (Exception e) {
            mostrarMensagemDivida("Erro ao liquidar: " + e.getMessage(), true);
        }
    }

    @FXML
    public void limparFormulario(ActionEvent event) {
        clienteSelecionado = null;
        txtNome.clear();
        txtTelefone.clear();
        txtNuit.clear();
        txtValorLiquidar.clear();
        lblNomeClienteDivida.setText("Nenhum cliente selecionado");
        lblSaldoDevedor.setText("0.00 MT");
        lblMensagemCrud.setText("");
        lblMensagemDivida.setText("");
        tabelaClientes.getSelectionModel().clearSelection();
    }

    private void mostrarMensagemCrud(String msg, boolean erro) {
        lblMensagemCrud.setText(msg);
        lblMensagemCrud.setStyle(erro ? "-fx-text-fill: #dc2626;" : "-fx-text-fill: #16a34a;");
    }

    private void mostrarMensagemDivida(String msg, boolean erro) {
        lblMensagemDivida.setText(msg);
        lblMensagemDivida.setStyle(erro ? "-fx-text-fill: #dc2626;" : "-fx-text-fill: #16a34a;");
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