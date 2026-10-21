package mz.novasys.model;
public class Cliente {
    private int id;
    private String nome;
    private String telefone;
    private String nuit;
    private double saldoDevedor;
    public Cliente() {}
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public String getNuit() { return nuit; }
    public void setNuit(String nuit) { this.nuit = nuit; }
    public double getSaldoDevedor() { return saldoDevedor; }
    public void setSaldoDevedor(double saldoDevedor) { this.saldoDevedor = saldoDevedor; }
    @Override
    public String toString() { return nome + " (" + (telefone != null ? telefone : "Sem Tel") + ")"; }
}