use glam::{DMat2, DVec2};
use crate::engine::Coordinate;

pub struct Layout {
    wheels: Vec<Wheel>,

}

pub struct Wheel {
    // how much of the total load this wheel carries
    load: f64,
    spring: f64,
}

pub fn calculate_load(touch_points: Vec<DVec2>, center_of_mass: DVec2) -> Vec<f64> {
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