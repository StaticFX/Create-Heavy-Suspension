use std::cmp::min;
use glam::{DMat2, DVec2, DVec3};
use crate::engine::is_usable_mass;
use crate::sable::MassData;

pub struct Layout {
    wheels: Vec<Wheel>,

}

pub struct Wheel {
    touch_point: DVec3,
    stiffness_multiplier: f64,
}

type Load = Vec<f64>;


pub fn calculate_load(touch_points: &[Wheel], center_of_mass: DVec2) -> Load {
    let len = touch_points.len() as f64;

    let center_of_wheels: DVec2 = touch_points.iter().map(|w| DVec2::new(w.touch_point.x, w.touch_point.z)).sum::<DVec2>() / len;
    let offsets: Vec<DVec2> = touch_points.iter().map(|w| DVec2::new(w.touch_point.x, w.touch_point.z)).map(|t| t - center_of_wheels).collect();
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

pub fn calculate_relative_spring_tension(wheels: Vec<Wheel>, load: Load, mass: MassData, dt: f64) -> f64 {
    let smallest_spring = wheels.iter()
        .map(|w| w.stiffness_multiplier)
        .min_by(f64::total_cmp)
        .unwrap_or(1f64);

    wheels
        .iter()
        .zip(load)
        .filter_map(|(wheel, fraction)| {
            let normal_mass = 1.0 / mass.inverse_normal_mass(wheel.touch_point, DVec3::Y);
            if !is_usable_mass(normal_mass) {
                return None;
            }
            let target = target_tension(mass.total_mass * fraction, smallest_spring);
            Some(max_tension(normal_mass, wheels.len(), dt) / target)
        })
        .fold(1.0, f64::min)

}

fn target_tension(load_share: f64, multiplier: f64) -> f64 {
    load_share * multiplier
}

pub fn max_tension(normal_mass: f64, wheel_count: usize, dt: f64) -> f64 {
    normal_mass / (wheel_count as f64 * dt * dt)
}