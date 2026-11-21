package je.vu.tvhome;

/** Finds an accent hue without retaining any identifiable image detail. */
final class ArtworkColors {
    static int accent(int[] pixels){
        double[] weight=new double[24],red=new double[24],green=new double[24],blue=new double[24];
        long totalR=0,totalG=0,totalB=0;int count=0;
        for(int pixel:pixels){if((pixel>>>24)<128)continue;int r=(pixel>>>16)&255,g=(pixel>>>8)&255,b=pixel&255;totalR+=r;totalG+=g;totalB+=b;count++;
            int max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b));double delta=max-min;if(max<35||delta<18)continue;
            double hue=max==r?(g-b)/delta:max==g?2+(b-r)/delta:4+(r-g)/delta;if(hue<0)hue+=6;int bin=Math.min(23,(int)(hue*4));double score=(delta/255.0)*(delta/255.0)*Math.sqrt(max/255.0);weight[bin]+=score;red[bin]+=r*score;green[bin]+=g*score;blue[bin]+=b*score;
        }
        int best=0;for(int i=1;i<24;i++)if(weight[i]>weight[best])best=i;
        if(weight[best]>.05)return 0xff000000|((int)(red[best]/weight[best])<<16)|((int)(green[best]/weight[best])<<8)|(int)(blue[best]/weight[best]);
        return count==0?0xff46515a:0xff000000|((int)(totalR/count)<<16)|((int)(totalG/count)<<8)|(int)(totalB/count);
    }
    static int darken(int accent,double amount){int r=(accent>>>16)&255,g=(accent>>>8)&255,b=accent&255;return 0xff000000|((int)(16+(r-16)*amount)<<16)|((int)(16+(g-16)*amount)<<8)|(int)(16+(b-16)*amount);}
}
