import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONObject;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class Main {

    public static void main(String[] args) throws IOException {
        int port = getPort();
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/hitung", new HitungHandler());
        server.start();
        System.out.println("✅ Server berjalan pada port: " + port + " — endpoint: /hitung");
    }

    private static int getPort() {
        String p = System.getenv("PORT");
        if (p != null) {
            try {
                return Integer.parseInt(p);
            } catch (NumberFormatException ignored) {}
        }
        return 8080;
    }

    static class HitungHandler implements HttpHandler {
        // pilih h yang cukup kecil untuk aproksimasi turunan pusat
        private static final double H = 1e-5;

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                // CORS headers (untuk akses dari GitHub Pages / browser)
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
                exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
                exchange.getResponseHeaders().add("Access-Control-Allow-Credentials", "true");

                // OPTIONS preflight
                if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }

                // hanya terima POST
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJson(exchange, 405, new JSONObject().put("error", "Gunakan metode POST"));
                    return;
                }

                // baca body
                String body = readRequestBody(exchange);
                if (body == null || body.trim().isEmpty()) {
                    sendJson(exchange, 400, new JSONObject().put("error", "Body request kosong"));
                    return;
                }

                JSONObject data;
                try {
                    data = new JSONObject(body);
                } catch (Exception e) {
                    sendJson(exchange, 400, new JSONObject().put("error", "JSON tidak valid: " + e.getMessage()));
                    return;
                }

                // ambil input
                String fx;
                double x, y, z;
                try {
                    fx = data.getString("fx");
                    x  = data.getDouble("x");
                    y  = data.getDouble("y");
                    z  = data.getDouble("z");
                } catch (Exception e) {
                    sendJson(exchange, 400, new JSONObject().put("error", "Field fx,x,y,z wajib dan harus valid: " + e.getMessage()));
                    return;
                }

                // sanitasi sederhana: pastikan fx tidak kosong
                if (fx == null || fx.trim().isEmpty()) {
                    sendJson(exchange, 400, new JSONObject().put("error", "Ekspresi fx tidak boleh kosong"));
                    return;
                }

                // lakukan perhitungan (turunan numerik pusat)
                try {
                    double dfdx = numericDerivative(fx, x, y, z, "x");
                    double dfdy = numericDerivative(fx, x, y, z, "y");
                    double dfdz = numericDerivative(fx, x, y, z, "z");
                    double fxyz = eval(fx, x, y, z);

                    // cek NaN / Infinity
                    if (!isFinite(dfdx) || !isFinite(dfdy) || !isFinite(dfdz) || !isFinite(fxyz)) {
                        sendJson(exchange, 400, new JSONObject().put("error", "Hasil tidak valid (NaN/Infinity). Periksa ekspresi dan nilai input."));
                        return;
                    }

                    // Bidang singgung untuk permukaan level F(x,y,z) = c adalah:
                    // Fx(x0,y0,z0)*(x-x0) + Fy*(y-y0) + Fz*(z-z0) = 0
                    String bidang = String.format(
                        "%.6f(x - %.6f) + %.6f(y - %.6f) + %.6f(z - %.6f) = 0",
                        dfdx, x, dfdy, y, dfdz, z
                    );

                    JSONObject hasil = new JSONObject();
                    hasil.put("dfdx", dfdx);
                    hasil.put("dfdy", dfdy);
                    hasil.put("dfdz", dfdz);
                    hasil.put("fxyz", fxyz);
                    hasil.put("bidang", bidang);

                    sendJson(exchange, 200, hasil);
                } catch (IllegalArgumentException iae) {
                    sendJson(exchange, 400, new JSONObject().put("error", "Ekspresi tidak valid: " + iae.getMessage()));
                } catch (Exception ex) {
                    ex.printStackTrace();
                    sendJson(exchange, 500, new JSONObject().put("error", "Kesalahan server: " + ex.getMessage()));
                }

            } finally {
                // nothing
            }
        }

        private String readRequestBody(HttpExchange exchange) throws IOException {
            InputStream is = exchange.getRequestBody();
            if (is == null) return null;
            try(BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                return sb.toString();
            }
        }

        private double numericDerivative(String f, double x, double y, double z, String var) {
            double h = H;
            switch (var) {
                case "x":
                    return (eval(f, x + h, y, z) - eval(f, x - h, y, z)) / (2 * h);
                case "y":
                    return (eval(f, x, y + h, z) - eval(f, x, y - h, z)) / (2 * h);
                case "z":
                    return (eval(f, x, y, z + h) - eval(f, x, y, z - h)) / (2 * h);
                default:
                    throw new IllegalArgumentException("Variabel turunan tidak dikenal: " + var);
            }
        }

        private boolean isFinite(double v) {
            return !Double.isNaN(v) && !Double.isInfinite(v);
        }

        private void sendJson(HttpExchange exchange, int statusCode, JSONObject obj) throws IOException {
            byte[] bytes = obj.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            // keep CORS headers (already added)
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private double eval(String f, double x, double y, double z) {
            // build expression with variables x,y,z
            // exp4j allows ^, sin, cos, log, etc.
            Expression e = new ExpressionBuilder(f)
                    .variables("x", "y", "z")
                    .build()
                    .setVariable("x", x)
                    .setVariable("y", y)
                    .setVariable("z", z);

            double res = e.evaluate();
            if (!isFinite(res)) {
                throw new IllegalArgumentException("Evaluasi menghasilkan NaN atau Infinity");
            }
            return res;
        }
    }
}
