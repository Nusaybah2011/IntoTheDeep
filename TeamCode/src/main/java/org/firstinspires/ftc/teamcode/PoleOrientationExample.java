/*
 * Copyright (c) 2020 OpenFTC Team
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.firstinspires.ftc.teamcode;

import static java.lang.Math.abs;
import static java.lang.Math.toRadians;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.openftc.easyopencv.OpenCvCamera;
import org.openftc.easyopencv.OpenCvCameraFactory;
import org.openftc.easyopencv.OpenCvCameraRotation;

/*
 * This is an advanced sample showcasing detecting and determining the orientation
 * of multiple poles, switching the viewport output, and communicating the results
 * of the vision processing to usercode.
 */
@TeleOp(name="Pole-Test", group="Skunkworks")
public class PoleOrientationExample extends LinearOpMode
{
    final int LOGSIZE = 10;
    double[]  angleOffset = new double[LOGSIZE];  // pixel offset error (left/right)
    double[]  distOffset  = new double[LOGSIZE];  // pixel offset error (too narrow/wide)

    // Vision stuff
    PowerPlaySuperPipeline pipelineLow;
    PowerPlaySuperPipeline pipelineFront;
    PowerPlaySuperPipeline pipelineBack;
    OpenCvCamera webcamLow = null;
    OpenCvCamera webcamFront = null;
    OpenCvCamera webcamBack = null;
    /* Declare OpMode members. */
    HardwareSlimbot robot = new HardwareSlimbot();
    boolean aligning = false;
    boolean ranging = false;
    boolean turretFacingFront = true;
    boolean cameraInitialized = false;
    PowerPlaySuperPipeline.AnalyzedPole thePole;

        /**
         * NOTE: Many comments have been omitted from this sample for the
         * sake of conciseness. If you're just starting out with EasyOpenCv,
         * you should take a look at or its
         * webcam counterpart,first.
         */


    @Override
    public void runOpMode() throws InterruptedException {
        telemetry.addLine("Robot initializing, wait for completion.");
        telemetry.update();

        int cameraMonitorViewId = hardwareMap.appContext.getResources().getIdentifier("cameraMonitorViewId", "id", hardwareMap.appContext.getPackageName());
        int[] viewportContainerIds = OpenCvCameraFactory.getInstance()
                .splitLayoutForMultipleViewports(
                        cameraMonitorViewId, //The container we're splitting
                        3, //The number of sub-containers to create
                        OpenCvCameraFactory.ViewportSplitMethod.HORIZONTALLY); //Whether to split the container vertically or horizontally
        webcamLow = OpenCvCameraFactory.getInstance().createWebcam(hardwareMap.get(WebcamName.class,
                "Webcam Low"), viewportContainerIds[0]);
        webcamLow.openCameraDeviceAsync(new OpenCvCamera.AsyncCameraOpenListener()
        {
            @Override
            public void onOpened()
            {
                pipelineLow = new PowerPlaySuperPipeline(true, false,
                        false, true, 160.0, true, false);
                webcamLow.setPipeline(pipelineLow);
                webcamLow.startStreaming(320, 240, OpenCvCameraRotation.UPRIGHT);
            }

            @Override
            public void onError(int errorCode)
            {
                // This will be called if the camera could not be opened
            }
        });
        webcamLow.showFpsMeterOnViewport(false);

        webcamFront = OpenCvCameraFactory.getInstance().createWebcam(hardwareMap.get(WebcamName.class,
                "Webcam Front"), viewportContainerIds[1]);
        webcamFront.openCameraDeviceAsync(new OpenCvCamera.AsyncCameraOpenListener()
        {
            @Override
            public void onOpened()
            {
                pipelineFront = new PowerPlaySuperPipeline(false, true,
                        false, false, 160.0, true, false);
                webcamFront.setPipeline(pipelineFront);
                webcamFront.startStreaming(320, 240, OpenCvCameraRotation.UPSIDE_DOWN);
            }

            @Override
            public void onError(int errorCode)
            {
                // This will be called if the camera could not be opened
            }
        });
        webcamFront.showFpsMeterOnViewport(false);

        webcamBack = OpenCvCameraFactory.getInstance().createWebcam(hardwareMap.get(WebcamName.class,
                "Webcam Back"), viewportContainerIds[2]);
        webcamBack.openCameraDeviceAsync(new OpenCvCamera.AsyncCameraOpenListener()
        {
            @Override
            public void onOpened()
            {
                pipelineBack = new PowerPlaySuperPipeline(false, true,
                        false, false, 160.0, true, false);
                webcamBack.setPipeline(pipelineBack);
                webcamBack.startStreaming(320, 240, OpenCvCameraRotation.UPRIGHT);
                cameraInitialized = true;
            }

            @Override
            public void onError(int errorCode)
            {
                // This will be called if the camera could not be opened
            }
        });
        webcamBack.showFpsMeterOnViewport(false);

        // Tell telemetry to update faster than the default 250ms period :)
        telemetry.setMsTransmissionInterval(20);
        while(!cameraInitialized) {
            sleep(100);
        }
        /* Declare OpMode members. */
        robot.init(hardwareMap,false);

        while(!cameraInitialized) {
            sleep(100);
        }

        telemetry.addLine("Robot initialized, ready to start.");
        telemetry.update();
        waitForStart();

        // Perform setup needed to center turret
        robot.turretPosInit( robot.TURRET_ANGLE_CENTER );

        while (opModeIsActive())
        {
            // Don't burn an insane amount of CPU cycles in this sample because
            // we're not doing anything else
            sleep(20);

            // Execute the automatic turret movement code   
            robot.readBulkData();
            robot.turretPosRun();

            // Let us see if we can use the camera for distance.
            alignToPole();
            telemetry.addLine("Aligned... waiting for kick");
            for( int index=0; index<LOGSIZE; index++ ) {
                telemetry.addData(" ","%d = %.1f %.1f pix",
                        index, angleOffset[index], distOffset[index] );
            }
            telemetry.update();
            sleep( 2000 );
        }

        // Close out the vision
        if(webcamLow != null) {
            webcamLow.stopStreaming();
        }
        if(webcamFront != null) {
            webcamFront.stopStreaming();
        }
        if(webcamBack != null) {
            webcamBack.stopStreaming();
        }
    }

    /*---------------------------------------------------------------------------------*/

    void alignToPole() {
        PowerPlaySuperPipeline.AnalyzedPole theLocalPole;
        final double TURN_SLOPE = 0.005787;
        final double TURN_OFFSET = 0.07422;
        final double DRIVE_SLOPE = 0.03173;
        final double DRIVE_OFFSET = 0.04828;

        double turnPower;
        double drivePower;
        theLocalPole = pipelineBack.getDetectedPole();
        while (opModeIsActive() && ((theLocalPole.alignedCount <= 3) || theLocalPole.properDistanceHighCount <= 3)) {
            if(theLocalPole.aligned) {
                turnPower = 0.0;
            } else {
                // Need to calculate the turn power based on pixel offset
                // Maximum number of pixels off would be half of 320, so 160.
                // The FOV is 48 degrees, so 0.15 degrees per pixel. This should
                // go 1.0 to 0.08 from 24 degrees to 0.
                turnPower = (theLocalPole.centralOffset > 0)?
                        (theLocalPole.centralOffset * TURN_SLOPE + TURN_OFFSET) :
                        (theLocalPole.centralOffset * TURN_SLOPE - TURN_OFFSET);
            }
            if(theLocalPole.properDistanceHigh) {
                drivePower = 0.0;
            } else {
                // Need to calculate the drive power based on pixel offset
                // Maximum number of pixels off would be in the order of 30ish.
                // This is a first guess that will have to be expiremented on.
                // Go 1.0 to 0.08 from 30 pixels to 2.
                drivePower = (theLocalPole.highDistanceOffset > 0 )?
                        (theLocalPole.highDistanceOffset * DRIVE_SLOPE + DRIVE_OFFSET) :
                        (theLocalPole.highDistanceOffset * DRIVE_SLOPE - DRIVE_OFFSET);
            }
            if(abs(drivePower) < 0.01 && abs(turnPower) < 0.01) {
                robot.stopMotion();
            } else {
                driveAtTurretAngle(drivePower, turnPower);
            }

            // Shift all previous instrumentation readings down one entry
            for( int index=1; index<LOGSIZE; index++ ) {
                angleOffset[index-1] = angleOffset[index];
                distOffset[index-1]  = distOffset[index];
            }
            // Add the latest numbers to the end
            angleOffset[LOGSIZE-1] = theLocalPole.centralOffset;
            distOffset[LOGSIZE-1]  = theLocalPole.highDistanceOffset;
            telemetry.addData("POLE","angle=%c distance=%c",
                    ((theLocalPole.aligned)? 'Y':'n'),
                    ((theLocalPole.properDistanceHigh)? 'Y':'n') );
            for( int index=0; index<LOGSIZE; index++ ) {
                telemetry.addData(" ","%d = %.1f  %.1f pix",
                        index, angleOffset[index], distOffset[index] );
            }
            telemetry.update();
            // sleep( 40 );
            theLocalPole = pipelineFront.getDetectedPole();
        }
        robot.stopMotion();
    } // alignToPole

    /*---------------------------------------------------------------------------------*/
    /*  AUTO: Drive at specified angle and power while turning at specified power.     */
    /*---------------------------------------------------------------------------------*/
    void driveAtTurretAngle(double drivePower, double turnPower) {
        double frontRight, frontLeft, rearRight, rearLeft, maxPower, xTranslation, yTranslation;
        double turretAngle = robot.turretAngle;

        if(!turretFacingFront) {
            // Correct the angle for the turret being in the back.
            turretAngle = AngleWrapDegrees( turretAngle + 180.0 );
        }
        // This corrects for our 0 being straight forward and left being negative
        yTranslation = drivePower * Math.sin(toRadians(turretAngle + 90.0));
        // Is this negative already accounted for in the below math?
        xTranslation = -drivePower * Math.cos(toRadians(turretAngle + 90.0));

        frontRight = yTranslation - xTranslation + turnPower;
        frontLeft  = yTranslation + xTranslation - turnPower;
        rearRight  = yTranslation + xTranslation + turnPower;
        rearLeft   = yTranslation - xTranslation - turnPower;

        // Normalize the values so none exceed +/- 1.0
        maxPower = Math.max( Math.max( Math.abs(rearLeft),  Math.abs(rearRight)  ),
                   Math.max( Math.abs(frontLeft), Math.abs(frontRight) ) );
        if (maxPower > 1.0)
        {
            rearLeft   /= maxPower;
            rearRight  /= maxPower;
            frontLeft  /= maxPower;
            frontRight /= maxPower;
        }
        // Update motor power settings:
        robot.driveTrainMotors( frontLeft, frontRight, rearLeft, rearRight );
    } // driveAtTurretAngle
}