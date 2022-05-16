package party.iroiro.lock;

class SomeException extends Exception {
    private final int i;

    SomeException(int i) {
        this.i = i;
    }

    public int getI() {
        return i;
    }
}
