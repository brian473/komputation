__device__ void setToNan(float* destination, int start, int end) {

    for(int index = start; index < end; index++) {

        destination[index] = nanf("NaN");

    }

}