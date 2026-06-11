package com.liu.eemrsserver.memory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "memory")
public class MemoryProperties {
    private ShortTerm shortTerm = new ShortTerm();
    private Vector vector = new Vector();

    public ShortTerm getShortTerm() {
        return shortTerm;
    }

    public void setShortTerm(ShortTerm shortTerm) {
        this.shortTerm = shortTerm;
    }

    public Vector getVector() {
        return vector;
    }

    public void setVector(Vector vector) {
        this.vector = vector;
    }

    public static class ShortTerm {
        private long ttlHours = 24;

        public long getTtlHours() {
            return ttlHours;
        }

        public void setTtlHours(long ttlHours) {
            this.ttlHours = ttlHours;
        }
    }

    public static class Vector {
        private boolean enabled = false;
        private String collection = "medical_user_memory";
        private String serviceUrl = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCollection() {
            return collection;
        }

        public void setCollection(String collection) {
            this.collection = collection;
        }

        public String getServiceUrl() {
            return serviceUrl;
        }

        public void setServiceUrl(String serviceUrl) {
            this.serviceUrl = serviceUrl;
        }
    }
}
