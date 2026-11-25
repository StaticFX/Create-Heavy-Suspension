use glam::DVec3;
use crate::engine::is_usable_mass;
use crate::sable::MassData;
use crate::wheels::Wheel;

// TODO: Make config derived
pub const SPRING_FREQUENCY: i32 = 10;
pub const SPRING_STABILITY: f64 = 1.5;
pub const DAMPING_RATIO: f64 = 0.7;

#[derive(Clone, Copy, Debug)]
pub struct Suspension {
    pub springiness: f64,
    pub heave_damping: f64,
    pub rotation_damping: f64
}

pub struct SuspensionConfig {
    spring_frequency: i32,
    spring_stability: f64,
    damping_ration: f64,
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

fn critical_damping(spring: f64, mass: f64) -> f64 {
    2.0 * DAMPING_RATIO * (spring * mass).sqrt()
}

pub fn suspension_for_normal_mass(
    total_mass: f64,
    normal_mass: f64,
    load_fraction: f64,
    stiffness_multiplier: f64,
    spring_scale: f64,
    wheel_count: usize,
    dt: f64,
) -> Option<Suspension> {
    if !is_usable_mass(normal_mass) || !is_usable_mass(total_mass) || wheel_count == 0 {
        return None;
    }

    let wheels = wheel_count as f64;
    let load_share = total_mass * load_fraction;

    let spring = (target_tension(load_share, stiffness_multiplier) * spring_scale).min(max_tension(normal_mass, wheel_count, dt));
    let heave_damping = critical_damping(spring, load_share).min(total_mass / (wheels * dt));
    let rotation_damping = critical_damping(spring, normal_mass).min(normal_mass / (wheels * dt));

    Some(Suspension { springiness: spring, heave_damping, rotation_damping })
}

pub fn target_tension(load_share: f64, multiplier: f64) -> f64 {
    load_share * (SPRING_FREQUENCY as f64) * (SPRING_FREQUENCY as f64) * multiplier
}

pub fn max_tension(normal_mass: f64, wheel_count: usize, dt: f64) -> f64 {
    SPRING_STABILITY * normal_mass / (wheel_count as f64 * dt * dt)
}