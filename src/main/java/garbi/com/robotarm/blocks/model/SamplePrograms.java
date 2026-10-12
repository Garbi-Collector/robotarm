package garbi.com.robotarm.blocks.model;

public final class SamplePrograms {

    private SamplePrograms() {}

    /** Toma un objeto a un lado, lo lleva al otro y lo suelta, 2 veces. */
    public static Program pickAndPlace() {
        Program p = new Program("Pick and place");
        p.getBlocks().add(Block.home());
        p.getBlocks().add(Block.repeat(2,
                Block.openGripper(),
                Block.moveServo("Base", 45),
                Block.moveServo("Hombro", 140),
                Block.moveServo("Codo", 60),
                Block.closeGripper(),
                Block.waitSeconds(0.5),
                Block.moveServo("Hombro", 100),
                Block.moveServo("Base", 135),
                Block.moveServo("Hombro", 140),
                Block.openGripper(),
                Block.waitSeconds(0.5)));
        p.getBlocks().add(Block.home());
        return p;
    }
}