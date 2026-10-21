package mz.novasys.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import mz.novasys.database.Database;
import mz.novasys.util.Sessao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class LoginController {

    @FXML private TextField txtUtilizador;
    @FXML private PasswordField txtSenha;
    @FXML private TextField txtSenhaVisivel;
    @FXML private Button btnVerSenha;
    @FXML private Label lblMensagem;

    private boolean senhaVisivel = false;

    @FXML
    public void initialize() {
        if (txtSenhaVisivel != null && txtSenha != null) {
            txtSenhaVisivel.managedProperty().bind(txtSenha.visibleProperty().not());
            txtSenhaVisivel.visibleProperty().bind(txtSenha.visibleProperty().not());
        }

        javafx.event.EventHandler<javafx.scene.input.KeyEvent> enterHandler = event -> {
            if (event.getCode() == KeyCode.ENTER) {
                entrar(new ActionEvent());
            }
        };

        if (txtUtilizador != null) txtUtilizador.setOnKeyPressed(enterHandler);
        if (txtSenha != null) txtSenha.setOnKeyPressed(enterHandler);
        if (txtSenhaVisivel != null) txtSenhaVisivel.setOnKeyPressed(enterHandler);
    }

    @FXML
    public void alternarVisualizacaoSenha(ActionEvent event) {
        senhaVisivel = !senhaVisivel;
        if (senhaVisivel) {
            if (txtSenhaVisivel != null && txtSenha != null) txtSenhaVisivel.setText(txtSenha.getText());
            if (txtSenha != null) txtSenha.setVisible(false);
        } else {
            if (txtSenha != null && txtSenhaVisivel != null) txtSenha.setText(txtSenhaVisivel.getText());
            if (txtSenha != null) txtSenha.setVisible(true);
        }
    }

    private String getPasswordText() {
        if (senhaVisivel && txtSenhaVisivel != null) {
            return txtSenhaVisivel.getText().trim();
        }
        return txtSenha != null ? txtSenha.getText().trim() : "";
    }

    @FXML
    public void entrar(ActionEvent event) {
        String user = txtUtilizador != null ? txtUtilizador.getText().trim() : "";
        String senha = getPasswordText();

        if (user.isEmpty()) {
            exibirErro("Insira o nome de utilizador!");
            return;
        }

        if (senha.isEmpty()) {
            exibirErro("Insira a palavra-passe!");
            return;
        }

        try (Connection conn = Database.getConexao();
             PreparedStatement st = conn.prepareStatement("SELECT perfil FROM utilizadores WHERE username = ? AND senha = ?")) {
            st.setString(1, user);
            st.setString(2, senha);

            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    String perfil = rs.getString("perfil");
                    Sessao.perfilLogado = perfil != null ? perfil : "ADMINISTRADOR";

                    Scene scene = null;
                    if (event != null && event.getSource() instanceof Node) {
                        scene = ((Node) event.getSource()).getScene();
                    } else if (txtUtilizador != null && txtUtilizador.getScene() != null) {
                        scene = txtUtilizador.getScene();
                    }

                    if (scene != null) {
                        Parent root = FXMLLoader.load(getClass().getResource("/fxml/dashboard.fxml"));
                        scene.setRoot(root);
                    }
                } else {
                    exibirErro("Credenciais invalidas!");
                }
            }
        } catch (Exception e) {
            exibirErro("Erro de BD: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void exibirErro(String msg) {
        if (lblMensagem != null) {
            lblMensagem.setText(msg);
            lblMensagem.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
        }
    }
}