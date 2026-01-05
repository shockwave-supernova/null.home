package ohi.andre.consolelauncher.commands.main.raw;

import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;

public class donate implements CommandAbstraction {

    @Override
    public String exec(ExecutePack info) throws Exception {
        return "Feature disabled in this fork.";
    }

    @Override
    public int[] argType() {
        return new int[0];
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public int helpRes() {
        return 0;
    }

    @Override
    public String onArgNotFound(ExecutePack pack, int indexNotFound) {
        return "Argument not found";
    }

    @Override
    public String onNotArgEnough(ExecutePack pack, int nArgs) {
        return "Not enough arguments";
    }
}
