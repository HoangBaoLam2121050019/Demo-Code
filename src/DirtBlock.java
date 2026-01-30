public class DirtBlock extends Block {
    public DirtBlock() {
        this.name = "Dirt";
        this.breakable = true;
    }
    @Override
    public char getSymbol() {
        return 'D';
    }
}