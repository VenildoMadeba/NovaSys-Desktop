package mz.novasys.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import mz.novasys.database.Database;
import mz.novasys.util.Sessao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class UtilizadoresController {

    @FXML private TextField txtNome;
    @FXML private TextField txtUsername;
    @FXML private PasswordField txtSenha;
    @FXML private ComboBox<String> cmbPerfil;
    @FXML private Label lblMensagemCrud;

    @FXML private TableView<UtilizadorDTO> tabelaUtilizadores;
    @FXML private TableColumn<UtilizadorDTO, String> colNome;
    @FXML private TableColumn<UtilizadorDTO, String> colUsername;
    @FXML private TableColumn<UtilizadorDTO, String> colPerfil;

    private ObservableList<UtilizadorDTO> listaUtilizadores = FXCollections.observableArrayList();
    private UtilizadorDTO utilizadorSelecionado = null;

    // DTO Interno para Tabela
    public static class UtilizadorDTO {
        private int id;
        private String nome, username, perfil;
        public UtilizadorDTO(int id, String nome, String username, String perfil) {
            this.id = id; this.nome = nome; this.username = username; this.perfil = perfil;
        }
        public int getId() { return id; }
        public String getNome() { return nome; }
        public String getUsername() { return username; }
        public String getPerfil() { return perfil; }
    }

    @FXML
    public void initialize() {
        cmbPerfil.setItems(FXCollections.observableArrayList("ADMINISTRADOR", "OPERADOR"));
        cmbPerfil.getSelectionModel().selectFirst();

        colNome.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getNome()));
        colUsername.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getUsername()));
        colPerfil.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getPerfil()));
        tabelaUtilizadores.setItems(listaUtilizadores);

        carregarDados();
    }

    private void carregarDados() {
        listaUtilizadores.clear();
        try (Connection conn = Database.getConexao(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT id, nome, username, perfil FROM utilizadores ORDER BY nome ASC");
            while (rs.next()) {
                listaUtilizadores.add(new UtilizadorDTO(
                        rs.getInt("id"), rs.getString("nome"), rs.getString("username"), rs.getString("perfil")
                ));
            }
        } catch (Exception e) { mostrarMensagem("Erro BD: " + e.getMessage(), true); }
    }

    @FXML
    public void selecionarUtilizador(MouseEvent event) {
        utilizadorSelecionado = tabelaUtilizadores.getSelectionModel().getSelectedItem();
        if (utilizadorSelecionado != null) {
            txtNome.setText(utilizadorSelecionado.getNome());
            txtUsername.setText(utilizadorSelecionado.getUsername());
            txtSenha.clear(); // Por seguranca, nao carregar a senha
            cmbPerfil.setValue(utilizadorSelecionado.getPerfil());
            lblMensagemCrud.setText("");
        }
    }

    @FXML
    public void salvarUtilizador(ActionEvent event) {
        String nome = txtNome.getText().trim();
        String username = txtUsername.getText().trim();
        String senha = txtSenha.getText().trim();
        String perfil = cmbPerfil.getValue();

        if (nome.isEmpty() || username.isEmpty() || (utilizadorSelecionado == null && senha.isEmpty())) {
            mostrarMensagem("Nome, Username e Senha sao obrigatorios!", true);
            return;
        }

        try (Connection conn = Database.getConexao()) {
            if (utilizadorSelecionado == null) {
                // VERIFICAR UNIQUE USERNAME
                PreparedStatement check = conn.prepareStatement("SELECT id FROM utilizadores WHERE username = ?");
                check.setString(1, username);
                if (check.executeQuery().next()) {
                    mostrarMensagem("Este Nome de Acesso (Username) ja esta em uso!", true);
                    return;
                }

                PreparedStatement st = conn.prepareStatement("INSERT INTO utilizadores (nome, username, senha, perfil) VALUES (?, ?, ?, ?)");
                st.setString(1, nome); st.setString(2, username); st.setString(3, senha); st.setString(4, perfil);
                st.executeUpdate();
                mostrarMensagem("Utilizador registado!", false);
            } else {
                if (!senha.isEmpty()) {
                    PreparedStatement st = conn.prepareStatement("UPDATE utilizadores SET nome=?, username=?, senha=?, perfil=? WHERE id=?");
                    st.setString(1, nome); st.setString(2, username); st.setString(3, senha); st.setString(4, perfil); st.setInt(5, utilizadorSelecionado.getId());
                    st.executeUpdate();
                } else {
                    PreparedStatement st = conn.prepareStatement("UPDATE utilizadores SET nome=?, username=?, perfil=? WHERE id=?");
                    st.setString(1, nome); st.setString(2, username); st.setString(3, perfil); st.setInt(4, utilizadorSelecionado.getId());
                    st.executeUpdate();
                }
                
                // Se atualizou a si proprio e mudou o username
                if (Sessao.utilizadorLogado.equalsIgnoreCase(utilizadorSelecionado.getUsername())) {
                    Sessao.utilizadorLogado = username;
                    Sessao.perfilLogado = perfil;
                }
                mostrarMensagem("Utilizador atualizado!", false);
            }
            limparFormulario(null); carregarDados();
        } catch (Exception e) { mostrarMensagem("Erro: " + e.getMessage(), true); }
    }

    @FXML
    public void apagarUtilizador(ActionEvent event) {
        if (utilizadorSelecionado == null) {
            mostrarMensagem("Selecione um utilizador.", true);
            return;
        }

        // GUARD CLAUSE: Nao permitir apagar a si proprio
        if (Sessao.utilizadorLogado.equalsIgnoreCase(utilizadorSelecionado.getUsername())) {
            mostrarMensagem("Operacao Bloqueada: Nao pode apagar a si proprio.", true);
            return;
        }

        try (Connection conn = Database.getConexao()) {
            PreparedStatement st = conn.prepareStatement("DELETE FROM utilizadores WHERE id = ?");
            st.setInt(1, utilizadorSelecionado.getId());
            st.executeUpdate();
            mostrarMensagem("Utilizador apagado.", false);
            limparFormulario(null); carregarDados();
        } catch (Exception e) { mostrarMensagem("Erro: " + e.getMessage(), true); }
    }

    @FXML
    public void limparFormulario(ActionEvent event) {
        utilizadorSelecionado = null;
        txtNome.clear(); txtUsername.clear(); txtSenha.clear();
        lblMensagemCrud.setText(""); tabelaUtilizadores.getSelectionModel().clearSelection();
    }

    private void mostrarMensagem(String msg, boolean erro) {
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