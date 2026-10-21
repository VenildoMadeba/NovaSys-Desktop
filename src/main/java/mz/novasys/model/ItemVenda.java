package mz.novasys.model;
public class ItemVenda {
    private Produto produto;
    private double quantidade;
    private double precoUnitario;
    private double subtotal;
    public ItemVenda(Produto produto, double quantidade) {
        this.produto = produto;
        this.quantidade = quantidade;
        this.precoUnitario = produto.getPrecoVenda();
        this.subtotal = this.precoUnitario * quantidade;
    }
    public Produto getProduto() { return produto; }
    public String getNomeProduto() { return produto != null ? produto.getNome() : ""; }
    public double getQuantidade() { return quantidade; }
    public void setQuantidade(double quantidade) { 
        this.quantidade = quantidade; 
        this.subtotal = this.precoUnitario * quantidade;
    }
    public double getPrecoUnitario() { return precoUnitario; }
    public double getSubtotal() { return subtotal; }
}