package dev.folomkin.tasks;

import jakarta.persistence.*;

@Entity
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_seq")
    @SequenceGenerator(name = "user_seq", sequenceName = "users_id_seq", allocationSize = 50)
    private Long id;


    private String name;

    @Enumerated(EnumType.STRING)
    private RegionType region;

    public User() {
    }

    public User(String name, RegionType region) {
        this.name = name;
        this.region = region;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public RegionType getRegion() {
        return region;
    }

    public void setRegion(RegionType region) {
        this.region = region;
    }
}


