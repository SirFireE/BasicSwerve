// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems.swervedrive;

import java.util.function.DoubleSupplier;

import com.pathplanner.lib.auto.AutoBuilder;
import com.pathplanner.lib.path.PathConstraints;
import com.pathplanner.lib.util.HolonomicPathFollowerConfig;
import com.pathplanner.lib.util.ReplanningConfig;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.RobotBase;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Constants.AutoConstants;
import frc.robot.Constants.SwerveConstants;
import swervelib.SwerveDrive;
import swervelib.math.SwerveMath;
import swervelib.parser.SwerveParser;
import swervelib.telemetry.SwerveDriveTelemetry;
import swervelib.telemetry.SwerveDriveTelemetry.TelemetryVerbosity;

public class SwerveSubsystem extends SubsystemBase {

  private final SwerveDrive m_swerve;

  /** Creates a new SwerveSubsystem. */
  public SwerveSubsystem() {
    double angleConversionFactor = SwerveMath.calculateDegreesPerSteeringRotation(11.3142);
    double driveConversionFactor = SwerveMath.calculateMetersPerRotation(Units.inchesToMeters(4), 4.59);
    System.out.println("--- Conversion Factors ---");
    System.out.println("Angle: " + angleConversionFactor);
    System.out.println("Drive: " + driveConversionFactor);

    SwerveDriveTelemetry.verbosity = TelemetryVerbosity.HIGH;

    try {
      m_swerve = new SwerveParser(SwerveConstants.kConfigDirectory).createSwerveDrive(SwerveConstants.kMaxSpeed);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    m_swerve.setCosineCompensator(!RobotBase.isSimulation());

    setupPathPlanner();
  }

  @Override
  public void periodic() {
  }

  private void setupPathPlanner() {
    AutoBuilder.configureHolonomic(
        m_swerve::getPose, // Robot pose supplier
        m_swerve::resetOdometry, // Method to reset odometry (will be called if your auto has a starting pose)
        m_swerve::getRobotVelocity, // ChassisSpeeds supplier. MUST BE ROBOT RELATIVE
        m_swerve::setChassisSpeeds, // Method that will drive the robot given ROBOT RELATIVE ChassisSpeeds
        new HolonomicPathFollowerConfig( // HolonomicPathFollowerConfig, this should likely live in your Constants class
            AutoConstants.kTranslationPID, // Translation PID constants
            AutoConstants.kAnglePID, // Rotation PID constants
            SwerveConstants.kMaxSpeed, // Max module speed, in m/s
            m_swerve.swerveDriveConfiguration.getDriveBaseRadiusMeters(), // Drive base radius in meters. Distance from
                                                                          // robot center to furthest module.
            new ReplanningConfig() // Default path replanning config. See the API for the options here
        ),
        () -> {
          // Boolean supplier that controls when the path will be mirrored for the red
          // alliance
          // This will flip the path being followed to the red side of the field.
          // THE ORIGIN WILL REMAIN ON THE BLUE SIDE
          var alliance = DriverStation.getAlliance();
          return alliance.isPresent() ? alliance.get() == DriverStation.Alliance.Red : false;
        },
        this // Reference to this subsystem to set requirements
    );
  }

  public Command driveToPose(Pose2d pose) {
    // Create the constraints to use while pathfinding
    PathConstraints constraints = new PathConstraints(
        m_swerve.getMaximumVelocity(), 4.0,
        m_swerve.getMaximumAngularVelocity(), Units.degreesToRadians(720));

    // Since AutoBuilder is configured, we can use it to build pathfinding commands
    return AutoBuilder.pathfindToPose(
        pose,
        constraints,
        0.0, // Goal end velocity in meters/sec
        0.0 // Rotation delay distance in meters. This is how far the robot should travel
            // before attempting to rotate.
    );
  }

  public Command driveCommand(DoubleSupplier translationX, DoubleSupplier translationY,
      DoubleSupplier angularRotationX) {
    return run(() -> {
      // Make the robot move
      m_swerve.drive(
          new Translation2d(
              Math.pow(translationX.getAsDouble(), 3) * m_swerve.getMaximumVelocity(),
              Math.pow(translationY.getAsDouble(), 3) * m_swerve.getMaximumVelocity()),
          Math.pow(angularRotationX.getAsDouble(), 3) * m_swerve.getMaximumAngularVelocity(),
          true, false);
    });
  }

  public void addFakeVisionReading() {
    m_swerve.addVisionMeasurement(new Pose2d(3, 3, Rotation2d.fromDegrees(65)), Timer.getFPGATimestamp());
  }
}