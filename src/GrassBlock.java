public class GrassBlock extends Block {
    public GrassBlock() {
        this.name = "Grass";
        this.breakable = true;
    }
    @Override
    public char getSymbol() {
        return 'G';
    }
    @Override
    public boolean isWalkable() {
        return false;
    }
}