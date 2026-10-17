import com.blankdglab.config.GlobalStrengthSettings;

public class GlobalStrengthSettingsCheck {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("write")) {
            GlobalStrengthSettings.save(0.1, 0.2);
            int cap = 30;
            int a = Math.min(GlobalStrengthSettings.scale(1, Math.min(100, GlobalStrengthSettings.rawLimit(1, cap))), cap);
            int b = Math.min(GlobalStrengthSettings.scale(2, Math.min(100, GlobalStrengthSettings.rawLimit(2, cap))), cap);
            check(a == 10, "A: 100 x 0.1 must output 10 even when the configured cap is 30");
            check(b == 20, "B: 100 x 0.2 must output 20 even when the configured cap is 30");
            check(GlobalStrengthSettings.scale(1, 200) == 20, "A scaling failed");
            check(GlobalStrengthSettings.scale(2, 200) == 40, "B scaling failed");
            check(GlobalStrengthSettings.rawLimit(1, 0) == 0, "Zero limit must remain zero");
            System.out.println("write and arithmetic: OK");
        } else if (args.length == 1 && args[0].equals("read")) {
            check(GlobalStrengthSettings.multiplierA() == 0.1, "A setting was not loaded");
            check(GlobalStrengthSettings.multiplierB() == 0.2, "B setting was not loaded");
            GlobalStrengthSettings.save(0.0, 1.0);
            check(GlobalStrengthSettings.scale(1, 100) == 0, "Zero must disable A");
            check(GlobalStrengthSettings.scale(2, 100) == 100, "One must preserve B");
            System.out.println("load and edge values: OK");
        } else {
            throw new IllegalArgumentException("Use write or read");
        }
    }
}
