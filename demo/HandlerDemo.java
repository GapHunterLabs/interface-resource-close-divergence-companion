import java.sql.Connection;

interface ConnectionHandler {
    void handle(Connection conn) throws Exception;
}

class ClosingHandler implements ConnectionHandler {
    public void handle(Connection conn) throws Exception {
        conn.createStatement();
        conn.close();
    }
}

class LeakyHandler implements ConnectionHandler {
    public void handle(Connection conn) throws Exception {
        conn.createStatement();
    }
}

class Caller {
    void run(ConnectionHandler handler, Connection conn) throws Exception {
        handler.handle(conn);
    }
}
