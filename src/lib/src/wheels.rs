use std::cmp::min;
use glam::{DMat2, DVec2, DVec3};
use crate::engine::is_usable_mass;
use crate::sable::MassData;
use crate::suspension::Suspension;

pub struct Wheel {
    pub touch_point: DVec3,
    pub stiffness_multiplier: f64,
}

type Load = Vec<f64>;

pub struct WheelImpulses {
    pub at_wheel: f64,
    pub at_center_of_mass: f64
}

impl WheelImpulses {
    pub fn new(at_wheel: f64, at_center_of_mass: f64) -> Self {
        WheelImpulses {
            at_wheel,
            at_center_of_mass
        }
    }
}

impl Default for WheelImpulses {
    fn default() -> Self {
        WheelImpulses {
            at_wheel: 0.0,
            at_center_of_mass: 0.0,
        }
    }
}

pub fn calculate_load(wheels: &[Wheel], center_of_mass: DVec2) -> Load {
    let len = wheels.len() as f64;

    let center_of_wheels: DVec2 = wheels.iter().map(|w| DVec2::new(w.touch_point.x, w.touch_point.z)).sum::<DVec2>() / len;
    let offsets: Vec<DVec2> = wheels.iter().map(|w| DVec2::new(w.touch_point.x, w.touch_point.z)).map(|t| t - center_of_wheels).collect();
    let mass_offset = center_of_mass - center_of_wheels;

    let spread = offsets.iter().fold(
        DMat2::from_diagonal(DVec2::splat(1e-6)),
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

pub fn calculate_wheel_impulses(
    suspension: &Suspension,
    spring_compression: f64,
    wheel_velocity: f64,
    vehicle_velocity: f64,
    dt: f64
) -> WheelImpulses {
    if spring_compression <= 0.0 {
        return WheelImpulses::default();
    }

    let rotation_velocity = wheel_velocity - vehicle_velocity;
    let spring_force = suspension.springiness * spring_compression;
    let rotation_dampening = -suspension.rotation_damping * rotation_velocity;

    let velocity_at_wheel = ((spring_force + rotation_dampening) * dt).max(0.0);
    let heave_dampening = -suspension.heave_damping * vehicle_velocity;
    let at_com = (heave_dampening * dt).max(-velocity_at_wheel);

    WheelImpulses::new(velocity_at_wheel, at_com)
}
