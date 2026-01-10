package ping

import (
	"bufio"
	"bytes"
	"fmt"
	"log"
	"os"
	"os/exec"
	"strings"
	"time"
)

var Error = log.New(os.Stderr, "\u001b[31mERROR: \u001b[0m", log.LstdFlags|log.Lshortfile)

type (
	Result struct {
		IpAddress string
		Delay     string
		Status    int32
	}
)

var command = map[bool]string{true: "/system/bin/ping6", false: "/system/bin/ping"}

func Ping(ipAddress string, ttl byte, isIpv6 bool) *Result {
	result := &Result{}
	buf := new(bytes.Buffer)
	cmd := exec.Command(command[isIpv6], "-c1", fmt.Sprintf("-t%d", ttl), ipAddress)
	cmd.Stdout = buf
	/* stdout, err := cmd.StdoutPipe()
	if err != nil {
		result.Status = 2
		return result
	} */
	reader := bufio.NewReader(buf)
	start := time.Now()
	err := cmd.Run()
	delayNano := time.Since(start).Nanoseconds()
	delayMilli := float64(delayNano) / float64(time.Millisecond.Nanoseconds())
	result.Delay = fmt.Sprintf("%.1f", delayMilli)
	if err != nil {
		if exitErr, ok := err.(*exec.ExitError); ok {
			result.Status = int32(exitErr.ExitCode())
			if result.Status == 1 {
				_, err := reader.ReadString('\n')
				if err != nil {
					Error.Println(err)
					result.Status = 2
					return result
				}
				output, err := reader.ReadString('\n')
				if err != nil {
					Error.Println(err)
					result.Status = 2
					return result
				}
				outputs := strings.Split(output, " ")
				if len(outputs) < 2 {
					Error.Println("output less then 2 line")
					result.Status = -1
					return result
				}
				if isIpv6 {
					result.IpAddress = outputs[1]
				} else {
					result.IpAddress = outputs[1][:len(outputs[1])-1]
				}
			} else {
				Error.Println(err)
			}
		} else {
			Error.Println(err)
			result.Status = 2
		}
		return result
	}
	_, err = reader.ReadString('\n')
	if err != nil {
		Error.Println(err)
		result.Status = 2
		return result
	}
	if _, err = reader.Discard(14); err != nil {
		Error.Println(err)
		result.Status = 2
		return result
	}
	output, err := reader.ReadString('\n')
	if err != nil {
		Error.Println(err)
		result.Status = 2
		return result
	}
	outputs := strings.Split(output, " ")
	result.IpAddress = outputs[0][:len(outputs[0])-1]
	fmt.Println(outputs[3])
	delayStr := outputs[3][5:]
	result.Delay = strings.TrimSpace(delayStr)
	result.Status = 0
	return result
}
