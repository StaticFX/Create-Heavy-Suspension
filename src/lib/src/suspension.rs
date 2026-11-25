use glam::DVec3;
use crate::engine::is_usable_mass;
use crate::sable::MassData;
use crate::wheels::Wheel;

// TODO: Make config derived
pub const BASE_SPRING_RATE: f64 = 25_000.0;
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

pub fn critical_damping(spring: f64, mass: f64) -> f64 {
    2.0 * DAMPING_RATIO * (spring * mass).sqrt()
}

pub fn suspension_for_normal_mass(
    total_mass: f64,
    normal_mass: f64,
    load_fraction: f64,
    stiffness_multiplier: f64,
    wheel_count: usize,
    touching_count: usize,
    dt: f64,
) -> Option<Suspension> {
    if !is_usable_mass(normal_mass) || !is_usable_mass(total_mass) || wheel_count == 0 {
        return None;
    }

    let wheels = wheel_count as f64;
    let contacts = damping_contacts(wheel_count, touching_count);
    let load_share = total_mass * load_fraction;

    let spring = spring_rate(stiffness_multiplier).min(max_tension(normal_mass, wheel_count, dt));
    let heave_damping = (critical_damping(spring, load_share) * wheels / contacts).min(total_mass / (contacts * dt));
    let rotation_damping = critical_damping(spring, normal_mass).min(normal_mass / (contacts * dt));

    Some(Suspension { springiness: spring, heave_damping, rotation_damping })
}

fn damping_contacts(wheel_count: usize, touching_count: usize) -> f64 {
    touching_count.max(wheel_count.div_ceil(2)).min(wheel_count) as f64
}

pub fn spring_rate(multiplier: f64) -> f64 {
    BASE_SPRING_RATE * multiplier
}

pub fn max_tension(normal_mass: f64, wheel_count: usize, dt: f64) -> f64 {
    SPRING_STABILITY * normal_mass / (wheel_count as f64 * dt * dt)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn tune(touching_count: usize) -> Suspension {
        suspension_for_normal_mass(4012.0, 159.0, 0.25, 4.0, 4, touching_count, 0.025).unwrap()
    }

    #[test]
    fn all_wheels_touching_keeps_old_caps() {
        let s = tune(4);
        assert!((s.rotation_damping - 159.0 / (4.0 * 0.025)).abs() < 1e-9);
    }

    #[test]
    fn one_wheel_touching_doubles_rotation_cap() {
        let s = tune(1);
        assert!((s.rotation_damping - 159.0 / (2.0 * 0.025)).abs() < 1e-9);
    }

    #[test]
    fn one_wheel_touching_doubles_heave_damping() {
        assert!((tune(1).heave_damping - 2.0 * tune(4).heave_damping).abs() < 1e-6);
    }

    #[test]
    fn spring_ignores_touching() {
        assert_eq!(tune(1).springiness, tune(4).springiness);
    }

    #[test]
    fn spring_does_not_depend_on_load() {
        let light = suspension_for_normal_mass(1000.0, 159.0, 0.25, 1.0, 4, 4, 0.025).unwrap();
        let heavy = suspension_for_normal_mass(8000.0, 159.0, 0.25, 1.0, 4, 4, 0.025).unwrap();
        assert_eq!(light.springiness, BASE_SPRING_RATE);
        assert_eq!(heavy.springiness, light.springiness);
    }

    #[test]
    fn damping_still_follows_load() {
        let light = suspension_for_normal_mass(1000.0, 159.0, 0.25, 1.0, 4, 4, 0.025).unwrap();
        let heavy = suspension_for_normal_mass(8000.0, 159.0, 0.25, 1.0, 4, 4, 0.025).unwrap();
        assert!(heavy.heave_damping > light.heave_damping);
    }

    #[test]
    fn stiff_dial_is_capped_for_stability() {
        let s = suspension_for_normal_mass(4012.0, 159.0, 0.25, 1000.0, 4, 4, 0.025).unwrap();
        assert!((s.springiness - max_tension(159.0, 4, 0.025)).abs() < 1e-6);
    }

    #[test]
    fn touching_never_exceeds_wheel_count() {
        assert_eq!(damping_contacts(4, 9), 4.0);
        assert_eq!(damping_contacts(4, 0), 2.0);
        assert_eq!(damping_contacts(1, 0), 1.0);
        assert_eq!(damping_contacts(3, 1), 2.0);
    }
}
