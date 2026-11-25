use glam::DVec3;

use crate::engine::is_usable_mass;
use crate::sable::MassData;
use crate::suspension::{DAMPING_RATIO, spring_rate};

const SOLVER_ITERATIONS: usize = 32;
const CONVERGED: f64 = 1e-4;
const UP: DVec3 = DVec3::Y;

#[derive(Clone, Copy, Debug)]
pub struct SolverWheel {
    pub contact: DVec3,
    pub multiplier: f64,
    pub load_fraction: f64,
    pub compression: f64,
    pub age_in_steps: f64,
}

#[derive(Clone, Copy, Debug)]
pub struct BodyVelocity {
    pub linear: DVec3,
    pub angular: DVec3,
}

struct Constraint {
    rotation_axis: DVec3,
    compliance: f64,
    target_velocity: f64,
}

type Impulses = Vec<f64>;

pub fn calculate_impulses(mass: &MassData, wheels: &[SolverWheel], velocity: &BodyVelocity, gravity: DVec3, dt: f64) -> Option<Impulses> {
    if !is_usable_mass(mass.total_mass) || dt <= 0.0 {
        return None;
    }

    let constraints: Vec<Option<Constraint>> = wheels
        .iter()
        .map(|wheel| constraint_for(mass, wheel, velocity, gravity, dt))
        .collect();

    let active: Vec<usize> = (0..wheels.len()).filter(|&i| constraints[i].is_some()).collect();
    let rows: Vec<&Constraint> = active.iter().filter_map(|&i| constraints[i].as_ref()).collect();
    let count = rows.len();

    let weighted_axes: Vec<DVec3> = rows.iter().map(|row| mass.inverse_inertia * row.rotation_axis).collect();
    let mut couplings = vec![0.0; count * count];
    for i in 0..count {
        for j in 0..count {
            couplings[i * count + j] = mass.inverse_mass + rows[i].rotation_axis.dot(weighted_axes[j]);
        }
    }

    let mut solved = vec![0.0; count];
    for _ in 0..SOLVER_ITERATIONS {
        let mut largest_change: f64 = 0.0;
        let mut largest_impulse: f64 = 0.0;
        for i in 0..count {
            let coupling_row = &couplings[i * count..(i + 1) * count];
            let velocity_change: f64 = coupling_row.iter().zip(&solved).map(|(coupling, impulse)| coupling * impulse).sum();
            let diagonal = coupling_row[i] + rows[i].compliance;
            let residual = rows[i].target_velocity - velocity_change - rows[i].compliance * solved[i];
            let updated = (solved[i] + residual / diagonal).max(0.0);
            largest_change = largest_change.max((updated - solved[i]).abs());
            largest_impulse = largest_impulse.max(updated);
            solved[i] = updated;
        }
        if largest_change <= CONVERGED * largest_impulse.max(1.0) {
            break;
        }
    }

    let mut impulses = vec![0.0; wheels.len()];
    for (row, &wheel) in active.iter().enumerate() {
        impulses[wheel] = solved[row];
    }

    Some(impulses)
}

fn constraint_for(mass: &MassData, wheel: &SolverWheel, velocity: &BodyVelocity, gravity: DVec3, dt: f64) -> Option<Constraint> {
    let lever = wheel.contact - mass.center_of_mass;
    let wheel_velocity = (velocity.linear + velocity.angular.cross(lever)).dot(UP);
    let compression = wheel.compression - wheel.age_in_steps * wheel_velocity * dt;
    if compression <= 0.0 {
        return None;
    }

    let spring = spring_rate(wheel.multiplier);
    let load = mass.total_mass * wheel.load_fraction;
    let damping = 2.0 * DAMPING_RATIO * (spring * load.max(0.0)).sqrt();
    let response = spring * dt + damping;
    if response <= 0.0 {
        return None;
    }

    let velocity_after_gravity = wheel_velocity + gravity.dot(UP) * dt;
    Some(Constraint {
        rotation_axis: lever.cross(UP),
        compliance: 1.0 / (dt * response),
        target_velocity: spring * compression / response - velocity_after_gravity,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::suspension::BASE_SPRING_RATE;
    use glam::DMat3;

    const GRAVITY: f64 = 9.81;
    const DT: f64 = 0.025;
    const REST_LENGTH: f64 = 1.0;

    struct Vehicle {
        mass: MassData,
        contacts: Vec<DVec3>,
        multipliers: Vec<f64>,
        height: f64,
        pitch: f64,
        roll: f64,
        velocity: BodyVelocity,
        last_compressions: Vec<f64>,
        lowest_impulse: f64,
        highest_speed: f64,
    }

    impl Vehicle {
        fn new(total_mass: f64, inertia: DVec3, multiplier: f64) -> Self {
            let contacts = vec![
                DVec3::new(1.5, 0.0, 3.0),
                DVec3::new(-1.5, 0.0, 3.0),
                DVec3::new(1.5, 0.0, -3.0),
                DVec3::new(-1.5, 0.0, -3.0),
            ];
            Vehicle {
                mass: MassData {
                    total_mass,
                    inverse_mass: 1.0 / total_mass,
                    center_of_mass: DVec3::ZERO,
                    inverse_inertia: DMat3::from_diagonal(inertia.recip()),
                },
                multipliers: vec![multiplier; contacts.len()],
                last_compressions: vec![-1.0; contacts.len()],
                contacts,
                height: REST_LENGTH + 0.5,
                pitch: 0.0,
                roll: 0.0,
                velocity: BodyVelocity { linear: DVec3::ZERO, angular: DVec3::ZERO },
                lowest_impulse: 0.0,
                highest_speed: 0.0,
            }
        }

        fn compression(&self, contact: DVec3) -> f64 {
            let height = self.height + self.roll * contact.x - self.pitch * contact.z;
            REST_LENGTH - height
        }

        fn step(&mut self) {
            let measured: Vec<f64> = self.contacts.iter().map(|&c| self.compression(c)).collect();
            let wheels: Vec<SolverWheel> = self
                .contacts
                .iter()
                .enumerate()
                .map(|(i, &contact)| SolverWheel {
                    contact,
                    multiplier: self.multipliers[i],
                    load_fraction: 1.0 / self.contacts.len() as f64,
                    compression: if i == 0 { measured[0] } else { self.last_compressions[i] },
                    age_in_steps: if i == 0 { 0.0 } else { 1.0 },
                })
                .collect();

            let impulses = calculate_impulses(&self.mass, &wheels, &self.velocity, DVec3::new(0.0, -GRAVITY, 0.0), DT).unwrap();

            for (i, &contact) in self.contacts.iter().enumerate() {
                self.lowest_impulse = self.lowest_impulse.min(impulses[i]);
                let lever = contact - self.mass.center_of_mass;
                self.velocity.linear += UP * impulses[i] * self.mass.inverse_mass;
                self.velocity.angular += self.mass.inverse_inertia * lever.cross(UP * impulses[i]);
            }
            self.velocity.linear.y -= GRAVITY * DT;

            self.height += self.velocity.linear.y * DT;
            self.pitch += self.velocity.angular.x * DT;
            self.roll += self.velocity.angular.z * DT;

            self.last_compressions = measured;
            self.highest_speed = self.highest_speed.max(self.velocity.linear.length()).max(self.velocity.angular.length());
        }

        fn run(&mut self, steps: usize) {
            for _ in 0..steps {
                self.step();
            }
        }

        fn average_compression(&self) -> f64 {
            self.contacts.iter().map(|&c| self.compression(c)).sum::<f64>() / self.contacts.len() as f64
        }
    }

    fn expected_sag(total_mass: f64, multiplier: f64) -> f64 {
        total_mass * GRAVITY / (4.0 * BASE_SPRING_RATE * multiplier)
    }

    #[test]
    fn rests_at_weight_divided_by_spring() {
        let mut vehicle = Vehicle::new(2012.0, DVec3::new(381.0, 894.0, 872.0), 1.0);
        vehicle.run(800);
        let sag = vehicle.average_compression();
        assert!((sag - expected_sag(2012.0, 1.0)).abs() < 0.01, "sag {sag}");
    }

    #[test]
    fn more_weight_sinks_deeper() {
        let mut light = Vehicle::new(2012.0, DVec3::new(381.0, 894.0, 872.0), 1.0);
        let mut heavy = Vehicle::new(4012.0, DVec3::new(1500.0, 2000.0, 2000.0), 1.0);
        light.run(800);
        heavy.run(800);
        assert!(heavy.average_compression() > light.average_compression());
    }

    #[test]
    fn stiff_springs_stay_stable_on_light_corners() {
        let mut vehicle = Vehicle::new(4012.0, DVec3::new(381.0, 894.0, 872.0), 1000.0);
        vehicle.run(800);
        assert!(vehicle.highest_speed < 10.0, "speed {}", vehicle.highest_speed);
        let sag = vehicle.average_compression();
        assert!((sag - expected_sag(4012.0, 1000.0)).abs() < 0.01, "sag {sag}");
    }

    #[test]
    fn tilted_landing_does_not_blow_up() {
        let mut vehicle = Vehicle::new(4012.0, DVec3::new(381.0, 894.0, 872.0), 2.0);
        vehicle.height = REST_LENGTH + 2.0;
        vehicle.pitch = 0.2;
        vehicle.roll = 0.1;
        vehicle.run(1200);
        assert!(vehicle.highest_speed < 20.0, "speed {}", vehicle.highest_speed);
        assert!(vehicle.pitch.abs() < 0.02 && vehicle.roll.abs() < 0.02, "pitch {} roll {}", vehicle.pitch, vehicle.roll);
    }

    #[test]
    fn never_pulls() {
        let mut vehicle = Vehicle::new(4012.0, DVec3::new(381.0, 894.0, 872.0), 2.0);
        vehicle.height = REST_LENGTH + 2.0;
        vehicle.pitch = 0.2;
        vehicle.run(1200);
        assert!(vehicle.lowest_impulse >= 0.0);
    }

    #[test]
    fn extremely_light_corners_stay_stable() {
        let mut vehicle = Vehicle::new(4012.0, DVec3::splat(60.0), 1000.0);
        vehicle.height = REST_LENGTH + 1.0;
        vehicle.pitch = 0.15;
        vehicle.run(1600);
        assert!(vehicle.highest_speed < 20.0, "speed {}", vehicle.highest_speed);
        assert!(vehicle.pitch.abs() < 0.02, "pitch {}", vehicle.pitch);
    }

    #[test]
    fn heavy_end_sits_lower() {
        let mut vehicle = Vehicle::new(2012.0, DVec3::new(381.0, 894.0, 872.0), 1.0);
        vehicle.mass.center_of_mass = DVec3::new(0.0, 0.0, 1.0);
        vehicle.run(1200);
        let front = vehicle.compression(vehicle.contacts[0]);
        let rear = vehicle.compression(vehicle.contacts[2]);
        assert!(front > rear, "front {front} rear {rear}");
    }

    #[test]
    fn airborne_wheels_do_nothing() {
        let vehicle = Vehicle::new(2012.0, DVec3::splat(1000.0), 1.0);
        let wheels = [SolverWheel { contact: DVec3::ZERO, multiplier: 1.0, load_fraction: 1.0, compression: -0.5, age_in_steps: 0.0 }];
        let impulses = calculate_impulses(&vehicle.mass, &wheels, &vehicle.velocity, DVec3::new(0.0, -GRAVITY, 0.0), DT).unwrap();
        assert_eq!(impulses, vec![0.0]);
    }
}
