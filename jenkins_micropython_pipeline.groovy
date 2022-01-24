pipeline {
    agent {label 'devkit'}

    stages {
        stage('Prepare') {
            options {
                timeout(time: 5, unit: 'MINUTES')
            }
            steps {
                echo'Check test board availability ...'
                sh'dfu-util -l | grep "A324F865D249154E" '
                sh'dfu-util -l | grep "997EE163D2A92656" '
                sh'dfu-util -l | grep "1CD0455DD7B1193F" '

                echo'Setup repository ...'
                git 'https://github.com/alphaFred/micropython.git'
                sh 'git reset --hard HEAD'
                sh 'git fetch --all --prune'
                sh 'git checkout origin/mimxrt/bootloader'
                sh 'git submodule update --init'
            }
        }

        stage('Build') {
            options {
                timeout(time: 20, unit: 'MINUTES')
            }
            steps {
                dir('ports/mimxrt/') {
                    sh '''
                        if [ "${REBUILD_BINARIES}" = true ]
                        then
                            for board in MIMXRT1020_EVK SEEED_ARCH_MIX TEENSY41
                            do
                                make clean BOARD="$board"
                                make all BOARD="$board" DEBUG=1
                            done
                        fi
                    '''
                }
            }
        }

        stage("Stand-Alone Tests") {
            options {
                timeout(time: 15, unit: 'MINUTES')
            }
            failFast false
            parallel {
                stage('MIMXRT1020_EVK') {
                    steps {
                        dir('ports/mimxrt/') {
                            sh'''
                                touch build-MIMXRT1020_EVK/script.jlink
                                echo "ExitOnError 1" > build-MIMXRT1020_EVK/script.jlink
                                echo "speed auto" >> build-MIMXRT1020_EVK/script.jlink
                                echo "r" >> build-MIMXRT1020_EVK/script.jlink
                                echo "st" >> build-MIMXRT1020_EVK/script.jlink
                                echo "loadfile \"/home/jenkins/workspace/MicroPython/ports/mimxrt/build-MIMXRT1020_EVK/firmware.bin\" 0x60000000" >> build-MIMXRT1020_EVK/script.jlink
                                echo "qc" >> build-MIMXRT1020_EVK/script.jlink
                                /usr/bin/JLinkExe -device MIMXRT1021DAG5A -if SWD -CommanderScript build-MIMXRT1020_EVK/script.jlink
                            '''
                            sh'sleep 2s'
                            sh'dfu-util -l | grep "A324F865D249154E" | grep "f055:9802"'
                        }
                        dir('tests/') {
                            sh'''
                                TTY=$(ls -l /dev/serial/by-id | grep "A324F865D249154E" | grep -oP "tty\\w*\\d*")
                                python3.9 run-perfbench.py -d /dev/${TTY} -p 500000000 128 perf_bench/*viper* perf_bench/misc_aes.py perf_bench/bm_float.py > ../ports/mimxrt/build-MIMXRT1020_EVK/perfbench_results_MIMXRT1020_EVK.txt
                            '''
                        }
                        dir('tools/') {
                            sh'''
                                TTY=$(ls -l /dev/serial/by-id | grep "A324F865D249154E" | grep -oP "tty\\w*\\d*")
                                python3.9 pyboard.py -d /dev/${TTY} ../../../scripts/vfstest.py
                            '''
                            sh'''
                                TTY=$(ls -l /dev/serial/by-id | grep "A324F865D249154E" | grep -oP "tty\\w*\\d*")
                                python3.9 pyboard.py -d /dev/${TTY} ../../../scripts/sdcardtests.py
                            '''
                        }                        
                    }
                }
            }         
        }

        stage("Bootloader Tests") {
            options {
                timeout(time: 15, unit: 'MINUTES')
            }
            failFast false
            parallel {            
                stage('MIMXRT1020_EVK') {
                    steps {
                        dir('ports/mimxrt/') {
                            sh'''
                                touch build-MIMXRT1020_EVK/script.jlink
                                echo "ExitOnError 1" > build-MIMXRT1020_EVK/script.jlink
                                echo "speed auto" >> build-MIMXRT1020_EVK/script.jlink
                                echo "r" >> build-MIMXRT1020_EVK/script.jlink
                                echo "st" >> build-MIMXRT1020_EVK/script.jlink
                                echo "loadfile \"/home/jenkins/workspace/MicroPython/ports/mimxrt/build-MIMXRT1020_EVK/bootloader/bootloader.bin\" 0x60000000" >> build-MIMXRT1020_EVK/script.jlink
                                echo "qc" >> build-MIMXRT1020_EVK/script.jlink
                                /usr/bin/JLinkExe -device MIMXRT1021DAG5A -if SWD -CommanderScript build-MIMXRT1020_EVK/script.jlink
                            '''
                            sh'sleep 2s'
                            sh'dfu-util -l | grep "A324F865D249154E" | grep "cafe:4000"'
                        }

                        script{
                            test_firmware_update("MIMXRT1020_EVK", "A324F865D249154E", 5)
                        }
                    }
                }
                stage('SEEED_ARCH_MIX') {
                    steps {
                        script{
                            test_firmware_update("SEEED_ARCH_MIX", "997EE163D2A92656", 5)
                        }
                    }
                }
                stage('TEENSY41') {
                    steps {
                        script{
                            test_firmware_update("TEENSY41", "1CD0455DD7B1193F", 5)
                        }
                    }
                }
            }
        }

        stage('Functional Tests') {
            options {
                timeout(time: 15, unit: 'MINUTES')
            }
            failFast false
            parallel {
                stage('MIMXRT1020_EVK') {
                    steps{
                        dir('tests/') {
                            sh'''
                                TTY=$(ls -l /dev/serial/by-id | grep "A324F865D249154E" | grep -oP "tty\\w*\\d*")
                                python3.9 run-perfbench.py -d /dev/${TTY} -p 500000000 128 perf_bench/*viper* perf_bench/misc_aes.py perf_bench/bm_float.py > ../ports/mimxrt/build-MIMXRT1020_EVK/perfbench_results_MIMXRT1020_EVK.txt
                            '''
                        }
                        dir('tools/') {
                            sh'''
                                TTY=$(ls -l /dev/serial/by-id | grep "A324F865D249154E" | grep -oP "tty\\w*\\d*")
                                python3.9 pyboard.py -d /dev/${TTY} ../../../scripts/vfstest.py
                            '''
                            sh'''
                                TTY=$(ls -l /dev/serial/by-id | grep "A324F865D249154E" | grep -oP "tty\\w*\\d*")
                                python3.9 pyboard.py -d /dev/${TTY} ../../../scripts/sdcardtests.py
                            '''
                        }
                    }
                }
                stage('SEEED ARCH MIX') {
                    steps{
                        dir('tests/') {

                            sh'''
                                TTY=$(ls -l /dev/serial/by-id | grep "997EE163D2A92656" | grep -oP "tty\\w*\\d*")
                                python3.9 run-perfbench.py -d /dev/${TTY} -p 500000000 128 perf_bench/*viper* perf_bench/misc_aes.py perf_bench/bm_float.py > ../ports/mimxrt/build-SEEED_ARCH_MIX/perfbench_results_SEEED_ARCH_MIX.txt
                            '''
                            }
                        dir('tools/') {
                            sh'''
                                TTY=$(ls -l /dev/serial/by-id | grep "997EE163D2A92656" | grep -oP "tty\\w*\\d*")
                                python3.9 pyboard.py -d /dev/${TTY} ../../../scripts/vfstest.py
                            '''
                            sh'''
                                TTY=$(ls -l /dev/serial/by-id | grep "997EE163D2A92656" | grep -oP "tty\\w*\\d*")
                                python3.9 pyboard.py -d /dev/${TTY} ../../../scripts/sdcardtests.py
                            '''
                        }
                    }
                }
                stage('TEENSY41') {
                    steps{
                        dir('tests/') {
                            sh'''
                                TTY=$(ls -l /dev/serial/by-id | grep "1CD0455DD7B1193F" | grep -oP "tty\\w*\\d*")
                                python3.9 run-perfbench.py -d /dev/${TTY} -p 500000000 128 perf_bench/*viper* perf_bench/misc_aes.py perf_bench/bm_float.py > ../ports/mimxrt/build-TEENSY41/perfbench_results_TEENSY41.txt
                            '''

                        }
                        dir('tools/') {
                            sh'''
                                TTY=$(ls -l /dev/serial/by-id | grep "1CD0455DD7B1193F" | grep -oP "tty\\w*\\d*")
                                python3.9 pyboard.py -d /dev/${TTY} ../../../scripts/vfstest.py
                            '''
                            sh'''
                                TTY=$(ls -l /dev/serial/by-id | grep "1CD0455DD7B1193F" | grep -oP "tty\\w*\\d*")
                                python3.9 pyboard.py -d /dev/${TTY} ../../../scripts/sdcardtests.py
                            '''
                        }
                    }
                }
            }
        }

        stage('Archive') {
            options {
                timeout(time: 5, unit: 'MINUTES')
            }
            steps {
                dir('ports/mimxrt/') {
                archiveArtifacts artifacts: 'build-MIMXRT1020_EVK/*.uuc, build-MIMXRT1020_EVK/*.map, build-MIMXRT1020_EVK/*.elf, build-MIMXRT1020_EVK/*.bin, build-MIMXRT1020_EVK/*.hex, build-MIMXRT1020_EVK/bootloader/*.map, build-MIMXRT1020_EVK/bootloader/*.bin, build-MIMXRT1020_EVK/bootloader/*.hex, build-MIMXRT1020_EVK/perfbench_results_MIMXRT1020_EVK.txt', followSymlinks: false, onlyIfSuccessful: true
                archiveArtifacts artifacts: 'build-SEEED_ARCH_MIX/*.uuc, build-SEEED_ARCH_MIX/*.map, build-SEEED_ARCH_MIX/*.elf, build-SEEED_ARCH_MIX/*.bin, build-SEEED_ARCH_MIX/*.hex, build-SEEED_ARCH_MIX/bootloader/*.map, build-SEEED_ARCH_MIX/bootloader/*.bin, build-SEEED_ARCH_MIX/bootloader/*.hex, build-SEEED_ARCH_MIX/perfbench_results_SEEED_ARCH_MIX.txt', followSymlinks: false, onlyIfSuccessful: true
                archiveArtifacts artifacts: 'build-TEENSY41/*.uuc, build-TEENSY41/*.map, build-TEENSY41/*.elf, build-TEENSY41/*.bin, build-TEENSY41/*.hex, build-TEENSY41/bootloader/*.map, build-TEENSY41/bootloader/*.bin, build-TEENSY41/bootloader/*.hex, build-TEENSY41/perfbench_results_TEENSY41.txt', followSymlinks: false, onlyIfSuccessful: true
                }
            }
        }
    }
}

def test_firmware_update(String device_name, String device_serial, int n_times) {
    for(int i=0; i<n_times; i++) {
        dir('ports/mimxrt/') {
            sh("dfu-util -e -S ${device_serial}")
            sh("sleep 3s")
            sh("dfu-util -l | grep \"${device_serial}\" | grep \"cafe:4000\"")
            sh("sleep 1s")
            sh("dfu-util -D build-${device_name}/firmware.uuc -S ${device_serial} -R")
            sh("sleep 4s")
            sh("dfu-util -l | grep \"${device_serial}\" | grep \"f055:9802\"")
        }

        dir('tests/') {
            sh("TTY=\$(ls -l /dev/serial/by-id | grep \"${device_serial}\" | grep -oP \"tty\\w*\\d*\");python3.9 run-perfbench.py -d /dev/\${TTY} -p 500000000 128 perf_bench/misc_aes.py")
        }
    }
}