package mz.novasys.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
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
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import mz.novasys.database.Database;
import mz.novasys.util.Sessao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CaixaController {

    @FXML private Label lblDataHoraAtual;
    @FXML private Label lblStatusCaixa;
    @FXML private VBox cardAbertura;
    @FXML private VBox cardOperacoes;

    @FXML private TextField txtSaldoInicial;
    @FXML private TextField txtValorOperacao;
    @FXML private TextField txtDescricaoOperacao;
    @FXML private Label lblMensagem;

    @FXML private Label lblTotalDinheiro;
    @FXML private Label lblTotalMobile;
    @FXML private Label lblTotalPOS;

    @FXML private TableView<MovimentoViewModel> tabelaMovimentos;
    @FXML private TableColumn<MovimentoViewModel, String> colDataHora;
    @FXML private TableColumn<MovimentoViewModel, String> colTipo;
    @FXML private TableColumn<MovimentoViewModel, String> colDescricao;
    @FXML private TableColumn<MovimentoViewModel, String> colFormaPag;
    @FXML private TableColumn<MovimentoViewModel, Double> colValor;

    private ObservableList<MovimentoViewModel> listaMovimentos = FXCollections.observableArrayList();
    private int sessaoActivaId = -1;

    public static class MovimentoViewModel {
        private String dataHora; private String tipo; private String descricao; private String formaPagamento; private double valor;
        public MovimentoViewModel(String dataHora, String tipo, String descricao, String formaPagamento, double valor) {
            this.dataHora = dataHora; this.tipo = tipo; this.descricao = descricao; this.formaPagamento = formaPagamento; this.valor = valor;
        }
        public String getDataHora() { return dataHora; }
        public String getTipo() { return tipo; }
        public String getDescricao() { return descricao; }
        public String getFormaPagamento() { return formaPagamento; }
        public double getValor() { return valor; }
    }

    @FXML
    public void initialize() {
        iniciarRelogio();
        configurarTabela();
        verificarEstadoCaixa();
    }

    private void iniciarRelogio() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        Timeline relogio = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (lblDataHoraAtual != null) {
                lblDataHoraAtual.setText("Data/Hora: " + LocalDateTime.now().format(formatter));
            }
        }));
        relogio.setCycleCount(Timeline.INDEFINITE);
        relogio.play();
    }

    private void configurarTabela() {
        colDataHora.setCellValueFactory(new PropertyValueFactory<>("dataHora"));
        colTipo.setCellValueFactory(new PropertyValueFactory<>("tipo"));
        colDescricao.setCellValueFactory(new PropertyValueFactory<>("descricao"));
        colFormaPag.setCellValueFactory(new PropertyValueFactory<>("formaPagamento"));
        colValor.setCellValueFactory(new PropertyValueFactory<>("valor"));
        tabelaMovimentos.setItems(listaMovimentos);
    }

    private void verificarEstadoCaixa() {
        sessaoActivaId = -1;
        try (Connection conn = Database.getConexao(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT * FROM sessoes_caixa WHERE status = 'ABERTO' ORDER BY id DESC LIMIT 1");
            if (rs.next()) {
                sessaoActivaId = rs.getInt("id");
                lblStatusCaixa.setText("CAIXA: ABERTO (Turno #" + sessaoActivaId + ")");
                lblStatusCaixa.setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold; -fx-font-size: 16px;");
                cardAbertura.setVisible(false); cardAbertura.setManaged(false);
                cardOperacoes.setVisible(true); cardOperacoes.setManaged(true);
                carregarMovimentosECalcularTotais(conn);
            } else {
                lblStatusCaixa.setText("CAIXA: FECHADO");
                lblStatusCaixa.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold; -fx-font-size: 16px;");
                cardAbertura.setVisible(true); cardAbertura.setManaged(true);
                cardOperacoes.setVisible(false); cardOperacoes.setManaged(false);
                listaMovimentos.clear();
                lblTotalDinheiro.setText("0.00 MT"); lblTotalMobile.setText("0.00 MT"); lblTotalPOS.setText("0.00 MT");
            }
        } catch (Exception e) {
            mostrarMensagem("Erro ao verificar caixa: " + e.getMessage(), true);
        }
    }

    private void carregarMovimentosECalcularTotais(Connection conn) throws Exception {
        listaMovimentos.clear();
        double dinheiro = 0.0, mobile = 0.0, pos = 0.0;

        // Buscar Saldo Inicial
        PreparedStatement stInit = conn.prepareStatement("SELECT saldo_inicial FROM sessoes_caixa WHERE id = ?");
        stInit.setInt(1, sessaoActivaId);
        ResultSet rsInit = stInit.executeQuery();
        if (rsInit.next()) {
            dinheiro += rsInit.getDouble("saldo_inicial");
        }

        // Buscar Movimentacoes
        PreparedStatement stMov = conn.prepareStatement("SELECT * FROM movimentos_caixa WHERE sessao_id = ? ORDER BY id DESC");
        stMov.setInt(1, sessaoActivaId);
        ResultSet rsMov = stMov.executeQuery();

        while (rsMov.next()) {
            String tipo = rsMov.getString("tipo");
            String forma = rsMov.getString("forma_pagamento");
            double valor = rsMov.getDouble("valor");

            listaMovimentos.add(new MovimentoViewModel(
                    rsMov.getString("data_hora"), tipo, rsMov.getString("descricao"), forma, valor
            ));

            if ("SUPRIMENTO".equals(tipo)) {
                dinheiro += valor;
            } else if ("SANGRIA".equals(tipo)) {
                dinheiro -= valor;
            } else if ("VENDA".equals(tipo)) {
                if ("DINHEIRO".equals(forma)) dinheiro += valor;
                else if ("MPESA".equals(forma) || "EMOLA".equals(forma)) mobile += valor;
                else if ("POS / CARTAO".equals(forma)) pos += valor;
            }
        }

        lblTotalDinheiro.setText(String.format("%.2f MT", dinheiro));
        lblTotalMobile.setText(String.format("%.2f MT", mobile));
        lblTotalPOS.setText(String.format("%.2f MT", pos));
    }

    @FXML
    public void abrirCaixa(ActionEvent event) {
        double saldoInicial = 0.0;
        try {
            if (!txtSaldoInicial.getText().trim().isEmpty()) {
                saldoInicial = Double.parseDouble(txtSaldoInicial.getText().trim().replace(",", "."));
            }
        } catch (NumberFormatException e) {
            mostrarMensagem("Informe um valor inicial valido!", true);
            return;
        }

        String dataHoraNow = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        try (Connection conn = Database.getConexao();
             PreparedStatement st = conn.prepareStatement("INSERT INTO sessoes_caixa (operador, data_abertura, saldo_inicial, status) VALUES (?, ?, ?, 'ABERTO')")) {
            st.setString(1, Sessao.utilizadorLogado);
            st.setString(2, dataHoraNow);
            st.setDouble(3, saldoInicial);
            st.executeUpdate();
            
            txtSaldoInicial.clear();
            mostrarMensagem("Caixa aberto com sucesso!", false);
            verificarEstadoCaixa();
        } catch (Exception e) {
            mostrarMensagem("Erro ao abrir caixa: " + e.getMessage(), true);
        }
    }

    @FXML
    public void registrarSuprimento(ActionEvent event) { registrarOperacao("SUPRIMENTO", "DINHEIRO"); }

    @FXML
    public void registrarSangria(ActionEvent event) { registrarOperacao("SANGRIA", "DINHEIRO"); }

    private void registrarOperacao(String tipo, String forma) {
        double valor = 0.0;
        try {
            valor = Double.parseDouble(txtValorOperacao.getText().trim().replace(",", "."));
            if (valor <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            mostrarMensagem("Informe um valor valido para a operacao!", true);
            return;
        }

        String desc = txtDescricaoOperacao.getText().trim();
        if (desc.isEmpty()) desc = tipo + " de Caixa";

        String dataHoraNow = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        try (Connection conn = Database.getConexao();
             PreparedStatement st = conn.prepareStatement("INSERT INTO movimentos_caixa (sessao_id, data_hora, tipo, descricao, valor, forma_pagamento) VALUES (?, ?, ?, ?, ?, ?)")) {
            st.setInt(1, sessaoActivaId);
            st.setString(2, dataHoraNow);
            st.setString(3, tipo);
            st.setString(4, desc);
            st.setDouble(5, valor);
            st.setString(6, forma);
            st.executeUpdate();

            txtValorOperacao.clear(); txtDescricaoOperacao.clear();
            mostrarMensagem(tipo + " registado com sucesso!", false);
            verificarEstadoCaixa();
        } catch (Exception e) {
            mostrarMensagem("Erro na operacao: " + e.getMessage(), true);
        }
    }

    @FXML
    public void fecharCaixa(ActionEvent event) {
        if (sessaoActivaId == -1) return;

        String dataHoraNow = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        try (Connection conn = Database.getConexao();
             PreparedStatement st = conn.prepareStatement("UPDATE sessoes_caixa SET status = 'FECHADO', data_fechamento = ? WHERE id = ?")) {
            st.setString(1, dataHoraNow);
            st.setInt(2, sessaoActivaId);
            st.executeUpdate();

            mostrarMensagem("Caixa fechado com sucesso!", false);
            verificarEstadoCaixa();
        } catch (Exception e) {
            mostrarMensagem("Erro ao fechar caixa: " + e.getMessage(), true);
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