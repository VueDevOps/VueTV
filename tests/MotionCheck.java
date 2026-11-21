package je.vu.tvhome;

public class MotionCheck {
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){
        for(double hz:new double[]{30,60,120}){
            MotionSpring spring=new MotionSpring(0);spring.target(800);double last=0;
            for(int i=0;i<hz;i++){spring.step(1/hz,22);check(spring.position>=last&&spring.position<=800,"No overshoot from rest");last=spring.position;}
            check(Math.abs(spring.position-800)<.5,"Settles at "+hz+"Hz");
        }
        MotionSpring spring=new MotionSpring(0);spring.target(800);spring.step(.08,22);
        double position=spring.position,velocity=spring.velocity;spring.target(200);
        check(spring.position==position&&spring.velocity==velocity,"Retarget must not jump or reset velocity");
        for(int i=0;i<120;i++)spring.step(1/60.0,22);
        check(spring.position==200&&spring.velocity==0,"Retarget settles exactly");
        spring.snap(0);check(spring.position==0&&spring.velocity==0,"Reduced motion snap");
        System.out.println("PASS: frame-rate independence, monotonic settling, continuous retargeting and reduced-motion snap");
    }
}
