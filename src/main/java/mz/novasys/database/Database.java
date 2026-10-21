package mz.novasys.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import mz.novasys.security.LicencaService;

public class Database {
    private static final String URL = "jdbc:sqlite:novasys.db";

    public static Connection getConexao() throws SQLException {
        Connection conn = DriverManager.getConnection(URL);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
        return conn;
    }

    public static void inicializarBanco() {
        try (Connection conn = getConexao(); Statement stmt = conn.createStatement()) {
            
            stmt.execute("CREATE TABLE IF NOT EXISTS utilizadores (id INTEGER PRIMARY KEY AUTOINCREMENT, nome TEXT NOT NULL, username TEXT UNIQUE NOT NULL, senha TEXT NOT NULL, perfil TEXT NOT NULL);");
            stmt.execute("CREATE TABLE IF NOT EXISTS clientes (id INTEGER PRIMARY KEY AUTOINCREMENT, nome TEXT NOT NULL, telefone TEXT, nuit TEXT, saldo_devedor REAL DEFAULT 0.0);");
            stmt.execute("CREATE TABLE IF NOT EXISTS produtos (id INTEGER PRIMARY KEY AUTOINCREMENT, nome TEXT NOT NULL, codigo_barras TEXT UNIQUE, preco_custo REAL DEFAULT 0.0, preco_venda REAL NOT NULL, stock_actual REAL DEFAULT 0.0, stock_minimo REAL DEFAULT 5.0);");
            stmt.execute("CREATE TABLE IF NOT EXISTS vendas (id INTEGER PRIMARY KEY AUTOINCREMENT, cliente_id INTEGER, data_venda TEXT NOT NULL, total_bruto REAL NOT NULL, desconto REAL DEFAULT 0.0, total_liquido REAL NOT NULL, forma_pagamento TEXT NOT NULL, valor_pago REAL DEFAULT 0.0, troco REAL DEFAULT 0.0, FOREIGN KEY (cliente_id) REFERENCES clientes(id));");
            stmt.execute("CREATE TABLE IF NOT EXISTS itens_venda (id INTEGER PRIMARY KEY AUTOINCREMENT, venda_id INTEGER NOT NULL, produto_id INTEGER NOT NULL, quantidade REAL NOT NULL, preco_unitario REAL NOT NULL, subtotal REAL NOT NULL, FOREIGN KEY (venda_id) REFERENCES vendas(id), FOREIGN KEY (produto_id) REFERENCES produtos(id));");
            stmt.execute("CREATE TABLE IF NOT EXISTS configuracoes (chave TEXT PRIMARY KEY, valor TEXT NOT NULL);");

            // TABELAS DO CAIXA COM DATA E HORA EXATAS
            stmt.execute("CREATE TABLE IF NOT EXISTS sessoes_caixa (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "operador TEXT NOT NULL, " +
                    "data_abertura TEXT NOT NULL, " +
                    "data_fechamento TEXT, " +
                    "saldo_inicial REAL DEFAULT 0.0, " +
                    "saldo_final REAL DEFAULT 0.0, " +
                    "status TEXT NOT NULL DEFAULT 'ABERTO');");

            stmt.execute("CREATE TABLE IF NOT EXISTS movimentos_caixa (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "sessao_id INTEGER NOT NULL, " +
                    "data_hora TEXT NOT NULL, " +
                    "tipo TEXT NOT NULL, " + // 'VENDA', 'SUPRIMENTO', 'SANGRIA'
                    "descricao TEXT, " +
                    "valor REAL NOT NULL, " +
                    "forma_pagamento TEXT NOT NULL, " +
                    "FOREIGN KEY (sessao_id) REFERENCES sessoes_caixa(id));");

            stmt.execute("INSERT OR IGNORE INTO utilizadores (id, nome, username, senha, perfil) VALUES (1, 'Administrador', 'admin', '123456', 'ADMINISTRADOR');");
            stmt.execute("INSERT OR IGNORE INTO configuracoes (chave, valor) VALUES ('LICENCA_TIPO', 'MENSAL');");
            stmt.execute("INSERT OR IGNORE INTO configuracoes (chave, valor) VALUES ('LICENCA_EXPIRACAO', date('now', '+7 days'));");
            stmt.execute("INSERT OR IGNORE INTO configuracoes (chave, valor) VALUES ('HARDWARE_ID', '" + LicencaService.getHardwareUUID() + "');");

        } catch (Exception e) {
            System.err.println("[Database] Erro: " + e.getMessage());
        }
    }
}