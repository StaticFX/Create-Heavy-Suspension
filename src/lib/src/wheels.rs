use std::cmp::min;
use glam::{DMat2, DVec2, DVec3};
use crate::engine::Coordinate;
use crate::sable::MassData;

pub struct Layout {
    wheels: Vec<Wheel>,

}

pub struct Wheel {
    // how much of the total load this wheel carries
    load: f64,
    spring: f64,
}

type Load = Vec<f64>;

pub fn calculate_load(touch_points: Vec<DVec2>, center_of_mass: DVec2) -> Load {
    let len = touch_points.len() as f64;

    let center_of_wheels: DVec2 = touch_points.iter().sum::<DVec2>() / len;
    let offsets: Vec<DVec2> = touch_points.iter().map(|t| t - center_of_wheels).collect();
    let mass_offset = center_of_wheels - center_of_mass;

    let spread = offsets.iter().fold(
        DMat2::from_diagonal(DVec2::splat(0f64)),
        |m, &o| m + DMat2::from_cols(o * o.x, o * o.y),
    );

    let shift = spread.inverse() * mass_offset;

    let fractions: Vec<f64> = offsets
        .iter()
        .map(|&o| (1.0 / len + o.dot(shift)).max(0.1f64 / len as f64))
        .collect();

    let total: f64 = fractions.iter().sum();
    fractions.iter().map(|f| f / total).collect()
}

pub fn calculate_relative_spring_tension(wheels: Vec<Wheel>, touch_points: Vec<DVec3>, load: Load, mass: MassData) -> f64 {
    let smallest_spring = wheels.iter()
        .map(|w| w.spring)
        .min_by(|w, o| w.total_cmp(o))
        .unwrap_or(1f64);

    let mut scale = 1f64;

    for (index, wheel) in wheels.iter().enumerate() {
        let touch_point = touch_points[index];
        let normalized_mass = 1f64 / mass.inverse_normal_mass(touch_point, DVec3::new(0f64,1f64,0f64));

        let target_tension =  mass.total_mass * load[index] * smallest_spring;
    }


}