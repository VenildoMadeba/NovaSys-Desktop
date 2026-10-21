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
import javafx.scene.layout.VBox;
import mz.novasys.database.Database;
import mz.novasys.security.LicencaService;
import mz.novasys.util.Sessao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class DashboardController {

    @FXML private Button btnProdutos;
    @FXML private Button btnStock;
    @FXML private Button btnRelatorios;
    @FXML private Button btnUtilizadores;
    @FXML private Label lblOperador;

    @FXML private ListView<String> listaNotificacoes;
    @FXML private Label lblStatusLicenca;
    @FXML private VBox painelRenovacao;
    @FXML private TextField txtChaveLicenca;
    @FXML private Label lblInfoLicenca;

    private ObservableList<String> alertas = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        if (listaNotificacoes != null) listaNotificacoes.setItems(alertas);
        
        lblOperador.setText("Operador: " + Sessao.utilizadorLogado + " | Perfil: " + Sessao.perfilLogado);
        aplicarPermissoesACL();
        
        verificarLicenca();
        verificarStockMinimo();
    }

    private void aplicarPermissoesACL() {
        if (!"ADMINISTRADOR".equalsIgnoreCase(Sessao.perfilLogado)) {
            // Se for OPERADOR, esconde menus restritos
            if (btnProdutos != null) { btnProdutos.setVisible(false); btnProdutos.setManaged(false); }
            if (btnStock != null) { btnStock.setVisible(false); btnStock.setManaged(false); }
            if (btnRelatorios != null) { btnRelatorios.setVisible(false); btnRelatorios.setManaged(false); }
            if (btnUtilizadores != null) { btnUtilizadores.setVisible(false); btnUtilizadores.setManaged(false); }
        }
    }

    private void verificarLicenca() {
        long dias = LicencaService.verificarDiasRestantes();
        if (dias == -999) {
            alertas.add("🚨 ALERTA CRITICO: Violacao de Hardware. Bloqueado.");
            lblStatusLicenca.setText("COPIA ILEGAL DETETADA");
        } else if (dias == -1) {
            lblStatusLicenca.setText("LICENCA VITALICIA ATIVA");
            painelRenovacao.setVisible(false);
            painelRenovacao.setManaged(false);
        } else {
            lblStatusLicenca.setText("LICENCA EXPIRA EM: " + dias + " DIAS");
            if (dias <= 5 && dias > 0) alertas.add("⚠️ Atencao: Licenca expira em " + dias + " dias.");
            else if (dias <= 0) alertas.add("⛔ LICENCA EXPIRADA! Renove agora.");
        }
    }

    private void verificarStockMinimo() {
        try (Connection conn = Database.getConexao(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS total FROM produtos WHERE stock_actual <= stock_minimo");
            if (rs.next() && rs.getInt("total") > 0) {
                alertas.add("📦 INVENTARIO: " + rs.getInt("total") + " produto(s) atingiram o stock minimo!");
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML
    public void validarLicenca(ActionEvent event) {
        if (LicencaService.renovarLicenca(txtChaveLicenca.getText().trim())) {
            alertas.clear(); txtChaveLicenca.clear();
            verificarLicenca(); verificarStockMinimo();
        } else {
            lblInfoLicenca.setText("Chave invalida! Tente novamente.");
            lblInfoLicenca.setStyle("-fx-text-fill: red;");
        }
    }

    private void navegarPara(ActionEvent event, String fxmlPath) {
        if (LicencaService.verificarDiasRestantes() == -999 || LicencaService.verificarDiasRestantes() == 0) return;
        try {
            Parent root = FXMLLoader.load(getClass().getResource(fxmlPath));
            Scene scene = ((Node) event.getSource()).getScene();
            scene.setRoot(root);
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML public void abrirVendas(ActionEvent event) { navegarPara(event, "/fxml/vendas.fxml"); }
    @FXML public void abrirClientes(ActionEvent event) { navegarPara(event, "/fxml/clientes.fxml"); }
    @FXML public void abrirProdutos(ActionEvent event) { navegarPara(event, "/fxml/produtos.fxml"); }
    @FXML public void abrirStock(ActionEvent event) { navegarPara(event, "/fxml/stock.fxml"); }
    @FXML public void abrirCaixa(ActionEvent event) { navegarPara(event, "/fxml/caixa.fxml"); }
    @FXML public void abrirRelatorios(ActionEvent event) { navegarPara(event, "/fxml/relatorios.fxml"); }
    @FXML public void abrirUtilizadores(ActionEvent event) { navegarPara(event, "/fxml/utilizadores.fxml"); }

    @FXML public void aoClicarSair(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/login.fxml"));
            Scene scene = ((Node) event.getSource()).getScene();
            scene.setRoot(root);
        } catch (Exception e) { e.printStackTrace(); }
    }
}