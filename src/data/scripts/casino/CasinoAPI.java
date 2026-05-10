package data.scripts.casino;

public class CasinoAPI {

    public static int getBalance() {
        return CasinoVIPManager.getBalance();
    }

    public static boolean canAfford(int amount) {
        return CasinoVIPManager.getBalance() >= amount;
    }

    public static void deduct(int amount) {
        CasinoVIPManager.addToBalance(-amount);
    }
}
