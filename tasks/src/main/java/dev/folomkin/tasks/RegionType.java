package dev.folomkin.tasks;

public enum RegionType {
    CENTRAL("Центральный"),
    SOUTH("Южный"),
    WEST("Западный");

    private final String description;
    RegionType(String description) { this.description = description; }
}