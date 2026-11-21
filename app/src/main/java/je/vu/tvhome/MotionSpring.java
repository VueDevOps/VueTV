package je.vu.tvhome;

/** Exact critically damped spring; retargeting preserves current velocity. */
final class MotionSpring {
    double position,velocity,target;
    MotionSpring(double position){snap(position);}
    void snap(double value){position=target=value;velocity=0;}
    void target(double value){target=value;}
    boolean step(double seconds,double frequency){
        double time=Math.max(0,Math.min(seconds,.05));
        double offset=position-target;
        double impulse=velocity+frequency*offset;
        double decay=Math.exp(-frequency*time);
        position=target+(offset+impulse*time)*decay;
        velocity=(velocity-frequency*impulse*time)*decay;
        if(Math.abs(position-target)<.4&&Math.abs(velocity)<3){snap(target);return false;}
        return true;
    }
}
