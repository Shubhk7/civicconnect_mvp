package com.civicconnect.backend.model;

import jakarta.persistence.*;
import org.locationtech.jts.geom.Polygon;

@Entity
@Table(name = "wards")
public class Ward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name;
    private String city;

    @Column(columnDefinition = "geometry(Polygon,4326)")
    private Polygon boundary;

    public Integer getId() { return id; }
    public String getName() { return name; }
    public String getCity() { return city; }
    public Polygon getBoundary() { return boundary; }
}
