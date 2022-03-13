import os
from machine import SDCard
import urandom
import utime

def bm_setup(params):
    nloop = params[0]
    file_sizes_large = (1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384)  # kBytes
    state = None

    def run():
        nonlocal state

        os.chdir("/sdcard")

        sdcard = SDCard()

        if not sdcard.present():
            state = False
            return 

        print("SDCard Info:", sdcard.info())
        test_data = os.urandom(1024)
        
        for _ in range(nloop):
            for file_size_large in file_sizes_large:
                with open("sd_stresstest_file_{}kBytes.txt".format(file_size_large), 'wb') as out_f:
                    start_ticks_us = utime.ticks_us()
                    for _ in range(file_size_large):
                        out_f.write(test_data)
                    end_ticks_us = utime.ticks_us()
                    print("Write perfomance {}kBytes {}s".format(file_size_large, (end_ticks_us - start_ticks_us) / 1e6))
                os.remove("sd_stresstest_file_{}kBytes.txt".format(file_size_large))
        state = True

    def result():
        return 1, state

    return run, result

if __name__ == "__main__":
    run, result = bm_setup([1,])
    run()
    result()
