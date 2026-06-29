package com.nexgate.model;

import java.util.List;

public class RouteConfig {
    private int port = 8080;
    private List<Route> routes;

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public List<Route> getRoutes() { return routes; }
    public void setRoutes(List<Route> routes) { this.routes = routes; }
}
