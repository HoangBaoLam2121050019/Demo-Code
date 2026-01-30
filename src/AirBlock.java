public class AirBlock extends Block {
    public AirBlock() {
        this.name = "Air";
        this.breakable = false;
    }
    @Override
    public char getSymbol() {
        return ' ';
    }
}