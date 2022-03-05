package Line;

import java.io.Serializable;

public class ServiceTunnel extends ServiceSection implements Serializable {
    public ServiceTunnel(String name) {
        super(name);

        this.numberOfServers = 1;
    }

    public ServiceTunnel() {
        this("Service Tunnel");
    }
}
