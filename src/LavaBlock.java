public class LavaBlock extends Block {
    public LavaBlock() {
        this.name = "Lava";
        this.breakable = false;
    }
    @Override
    public char getSymbol() {
        return '^';
    }
}