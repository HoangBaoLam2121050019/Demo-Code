public class StoneBlock extends Block {
    public StoneBlock() {
        this.name = "Stone";
        this.breakable = false;
    }
    @Override
    public char getSymbol() {
        return 'S';
    }
}