public abstract class Block {
    protected String name;
    protected boolean breakable;
    public String getName() {
        return name;
    }
    public boolean isBreakable() {
        return breakable;
    }
    public abstract char getSymbol();   
}