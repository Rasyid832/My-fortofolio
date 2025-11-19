import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONObject;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;


import java.io.*;
import java.net.InetSocketAddress;

public class Main {

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/hitung", new HitungHandler());
        server.start();
        System.out.println("✅ Server berjalan di http://localhost:8080/hitung");
    }

    static class HitungHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, "{\"error\":\"Gunakan metode POST\"}");
                return;
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), "utf-8"));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                body.append(line);
            }

            try {
                JSONObject data = new JSONObject(body.toString());
                String fx = data.getString("fx");
                double x = data.getDouble("x");
                double y = data.getDouble("y");
                double z = data.getDouble("z");

                double h = 0.0001;
                double dfdx = (eval(fx, x + h, y, z) - eval(fx, x - h, y, z)) / (2 * h);
                double dfdy = (eval(fx, x, y + h, z) - eval(fx, x, y - h, z)) / (2 * h);
                double dfdz = (eval(fx, x, y, z + h) - eval(fx, x, y, z - h)) / (2 * h);

                double fxyz = eval(fx, x, y, z);

                // bentuk bidang singgung
                String bidang = String.format(
                    "z - %.4f = %.4f(x - %.4f) + %.4f(y - %.4f) + %.4f(z - %.4f)",
                    fxyz, dfdx, x, dfdy, y, dfdz, z
                );

                JSONObject hasil = new JSONObject();
                hasil.put("dfdx", dfdx);
                hasil.put("dfdy", dfdy);
                hasil.put("dfdz", dfdz);
                hasil.put("fxyz", fxyz);
                hasil.put("bidang", bidang);

                sendResponse(exchange, hasil.toString());

            } catch (Exception e) {
                sendResponse(exchange, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }

        private double eval(String f, double x, double y, double z) {
            Expression e = new ExpressionBuilder(f)
                    .variables("x", "y", "z")
                    .build()
                    .setVariable("x", x)
                    .setVariable("y", y)
                    .setVariable("z", z);
            return e.evaluate();
        }

        private void sendResponse(HttpExchange exchange, String response) throws IOException {
            byte[] bytes = response.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }
}
