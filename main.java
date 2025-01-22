package com.eteks.test;

import com.eteks.sweethome3d.model.*;
import com.eteks.sweethome3d.plugin.Plugin;
import com.eteks.sweethome3d.plugin.PluginAction;
import com.eteks.sweethome3d.viewcontroller.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.swing.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.abs;

public class CombinedHttpServerPluginNew extends Plugin {
    private static final float wallHeight = 250;
    private static final float shuZhi = 1.5707964F; // 数值
    private HomeDoorOrWindow Door;
    private HomeDoorOrWindow Window;
    @Override
    public void init() {
        // 初始化门窗模型
        UserPreferences userPreferences = getUserPreferences();
        FurnitureCatalog furnitureCatalog = userPreferences.getFurnitureCatalog();
        FurnitureCategory furnitureCategory = furnitureCatalog.getCategory(3);
        for (CatalogPieceOfFurniture catalogPieceOfFurniture : furnitureCategory.getFurniture()) {
            if ("门".equals(catalogPieceOfFurniture.getName())) {
                Door = new HomeDoorOrWindow((DoorOrWindow) catalogPieceOfFurniture);
                System.out.println("找到门的模型");
            } else if ("窗户".equals(catalogPieceOfFurniture.getName())) {
                Window = new HomeDoorOrWindow((DoorOrWindow) catalogPieceOfFurniture);
                System.out.println("找到窗户的模型");
            }
        }

        try {
            // 启动HTTP服务器，监听8082端口
            HttpServer httpServer = HttpServer.create(new InetSocketAddress(8082), 0);
            httpServer.createContext("/delete-walls-windows-doors", new DeleteWallsWindowsDoorsHandler());
            httpServer.createContext("/create-wall", new WallHandler());
            httpServer.createContext("/create-door", new DoorHandler());
            httpServer.createContext("/create-window", new WindowHandler());
            httpServer.createContext("/export-obj", new ExportOBJHandler());
            httpServer.setExecutor(null); // 使用默认的线程池
            httpServer.start();
            System.out.println("HTTP服务器启动在端口8082");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 删除墙、窗户和门的处理器
    class DeleteWallsWindowsDoorsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            try {
                // 获取 Home 对象
                Home home = getHome();
                if (home == null) {
                    sendResponse(exchange, 500, "Home 对象未初始化");
                    return;
                }

                // 删除所有的墙、窗户和门
                deleteWallsWindowsDoors(home);

                // 返回成功响应
                sendResponse(exchange, 200, "成功删除所有的墙、窗户和门");
            } catch (Exception e) {
                sendResponse(exchange, 500, "删除墙、窗户和门时出错: " + e.getMessage());
            }
        }
        // 删除所有的墙、窗户和门
        private void deleteWallsWindowsDoors(Home home) {
            // 删除所有的墙
            List<Wall> walls = new ArrayList<>(home.getWalls()); // 复制一份墙的列表
            for (Wall wall : walls) {
                home.deleteWall(wall);
            }

            // 删除所有的门和窗户
            List<HomePieceOfFurniture> furniture = new ArrayList<>(home.getFurniture()); // 复制一份家具列表
            for (HomePieceOfFurniture piece : furniture) {
                if (piece instanceof DoorOrWindow) { // 判断是否为门或窗户
                    home.deletePieceOfFurniture(piece);
                }
            }
        }
    }

    // 创建墙的处理器
    class WallHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            try {
                String content = readInputStream(exchange.getRequestBody());
                String[] parts = content.split(",");
                if (parts.length != 6) {
                    sendResponse(exchange, 400, "Invalid input format. Expected: x1,y1,x2,y2,width,type");
                    return;
                }

                float x1 = Float.parseFloat(parts[0].trim());
                float y1 = Float.parseFloat(parts[1].trim());
                float x2 = Float.parseFloat(parts[2].trim());
                float y2 = Float.parseFloat(parts[3].trim());
                float width = Float.parseFloat(parts[4].trim());
                Wall wall = new Wall(x1, y1, x2, y2, width, wallHeight);

                SwingUtilities.invokeLater(() -> {
                    try {
                        Home home = getHome();
                        home.addWall(wall);
                        System.out.println("成功添加墙体: " + x1 + "," + y1 + " to " + x2 + "," + y2);
                    } catch (Exception e) {
                        System.out.println("添加墙体时出错: " + e.getMessage());
                        e.printStackTrace();
                    }
                });

                sendResponse(exchange, 200, "成功创建墙体");
            } catch (Exception e) {
                sendResponse(exchange, 500, "创建墙体时出错: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    // 创建门的处理器
    class DoorHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            try {
                String content = readInputStream(exchange.getRequestBody());
                String[] parts = content.split(",");
                if (parts.length != 6) {
                    sendResponse(exchange, 400, "Invalid input format. Expected: x1,y1,x2,y2,width,type");
                    return;
                }

                float x1 = Float.parseFloat(parts[0].trim());
                float y1 = Float.parseFloat(parts[1].trim());
                float x2 = Float.parseFloat(parts[2].trim());
                float y2 = Float.parseFloat(parts[3].trim());
                float depth = Float.parseFloat(parts[4].trim());
                String type = parts[5].trim();

                if (Door == null) {
                    sendResponse(exchange, 500, "Door model not found");
                    return;
                }
                float doorWidth;
                HomeDoorOrWindow door = new HomeDoorOrWindow(Door);
                if (type.equals("horizontal")) {
                    door.setAngle(0);
                } else if (type.equals("vertical")) {
                    door.setAngle(shuZhi);
                }
                door.setX((x1 + x2) / 2);
                door.setY((y1 + y2) / 2);
                door.setBoundToWall(true);
                SwingUtilities.invokeLater(() -> {
                    try {
                        Home home = getHome();
                        home.addPieceOfFurniture(door);

                        System.out.println("成功添加门: " + door.getX() + "," + door.getY());
                    } catch (Exception e) {
                        System.out.println("添加门时出错: " + e.getMessage());
                        e.printStackTrace();
                    }
                });

                sendResponse(exchange, 200, "成功创建门");
            } catch (Exception e) {
                sendResponse(exchange, 500, "创建门时出错: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    // 创建窗户的处理器
    class WindowHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            try {
                String content = readInputStream(exchange.getRequestBody());
                String[] parts = content.split(",");
                if (parts.length != 6) {
                    sendResponse(exchange, 400, "Invalid input format. Expected: x1,y1,x2,y2,width,type");
                    return;
                }

                float x1 = Float.parseFloat(parts[0].trim());
                float y1 = Float.parseFloat(parts[1].trim());
                float x2 = Float.parseFloat(parts[2].trim());
                float y2 = Float.parseFloat(parts[3].trim());
                float Depth = Float.parseFloat(parts[4].trim());
                String type = parts[5].trim();

                if (Window == null) {
                    sendResponse(exchange, 500, "Window model not found");
                    return;
                }
                float windowsWidth;
                HomeDoorOrWindow window = new HomeDoorOrWindow(Window);
                if (type.equals("horizontal")) {
                    window.setAngle(0);
                } else if (type.equals("vertical")) {
                    window.setAngle(shuZhi);
                }
                window.setX((x1 + x2) / 2);
                window.setY((y1 + y2) / 2);
                SwingUtilities.invokeLater(() -> {
                    try {
                        Home home = getHome();
                        home.addPieceOfFurniture(window);
                        System.out.println("成功添加窗户: " + window.getX() + "," + window.getY());
                    } catch (Exception e) {
                        System.out.println("添加窗户时出错: " + e.getMessage());
                        e.printStackTrace();
                    }
                });

                sendResponse(exchange, 200, "成功创建窗户");
            } catch (Exception e) {
                sendResponse(exchange, 500, "创建窗户时出错: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    // 导出OBJ文件的处理器
    class ExportOBJHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            try {
                // 读取请求体中的文件路径
                String filePath = readInputStream(exchange.getRequestBody());
                System.out.println("准备导出OBJ到路径: " + filePath);

                // 检查文件路径是否为空
                if (filePath == null || filePath.trim().isEmpty()) {
                    sendResponse(exchange, 400, "文件路径不能为空");
                    return;
                }

                // 检查文件路径是否合法
                if (!isValidFilePath(filePath)) {
                    sendResponse(exchange, 400, "文件路径不合法");
                    return;
                }

                // 获取 HomeView 并导出 OBJ 文件
                HomeView homeView = getHomeController().getView();
                try {
                    homeView.exportToOBJ(filePath); // 使用客户端传入的文件路径
                    sendResponse(exchange, 200, "OBJ文件导出成功: " + filePath);
                } catch (RecorderException e) {
                    throw new RuntimeException("导出OBJ文件失败: " + e.getMessage(), e);
                }
            } catch (Exception e) {
                String errorMessage = "处理导出OBJ请求时出错: " + e.getMessage();
                sendResponse(exchange, 500, errorMessage);
                e.printStackTrace();
            }
        }

        // 检查文件路径是否合法
        private boolean isValidFilePath(String filePath) {
            try {
                Paths.get(filePath); // 尝试解析路径
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    // 读取输入流为字符串
    private String readInputStream(InputStream inputStream) throws IOException {
        try (ByteArrayOutputStream result = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }
            return result.toString(StandardCharsets.UTF_8.name());
        }
    }

    // 发送HTTP响应
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    @Override
    public PluginAction[] getActions() {
        return new PluginAction[0];
    }
}
