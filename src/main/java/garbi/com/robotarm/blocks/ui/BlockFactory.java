package garbi.com.robotarm.blocks.ui;

import garbi.com.robotarm.blocks.model.Block;
import garbi.com.robotarm.blocks.model.BlockType;

/** Crea bloques nuevos con valores por defecto (los usa la paleta). */
public final class BlockFactory {

    private BlockFactory() {}

    public static Block create(BlockType type) {
        return switch (type) {
            case MOVE_SERVO   -> Block.moveServo("Base", 90);
            case OPEN_GRIPPER -> Block.openGripper();
            case CLOSE_GRIPPER-> Block.closeGripper();
            case WAIT         -> Block.waitSeconds(1);
            case HOME         -> Block.home();
            case REPEAT       -> Block.repeat(2);
            case CALL_METHOD  -> Block.callMethod(null);
        };
    }
}