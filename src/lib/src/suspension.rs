use serde::Serialize;
use tsify::Tsify;

pub const SPRING_FREQUENCY: i32 = 10;
pub const SPRING_STABILITY: f64 = 1.5;

#[derive(Clone, Copy, Debug, Tsify, Serialize)]
pub struct Suspension {
    pub springiness: f64,
    pub heave_damping: f64,
    pub rotation_damping: f64
}

impl Suspension {
    pub fn new(springiness: f64, heave_damping: f64, rotation_damping: f64) -> Self {
        Suspension {
            springiness,
            heave_damping,
            rotation_damping
        }
    }
}


pub fn target_tension(load_share: f64, multiplier: f64) -> f64 {
    load_share * (SPRING_FREQUENCY as f64) * (SPRING_FREQUENCY as f64) * multiplier
}

pub fn max_tension(normal_mass: f64, wheel_count: usize, dt: f64) -> f64 {
    SPRING_STABILITY * normal_mass / (wheel_count as f64 * dt * dt)
}