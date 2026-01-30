public class WoodBlock extends Block {
    public WoodBlock() {
        this.name = "Wood";
        this.breakable = true;
    }
    @Override
    public char getSymbol() {
        return 'W';
    }
}