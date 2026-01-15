package ping

import (
	"bufio"
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/exec"
	"strings"
	"time"
)

var logger = log.New(os.Stderr, "\u001b[31mERROR: \u001b[0m", log.LstdFlags|log.Lshortfile)

type (
	Result struct {
		IpLookupStatus bool
		IpAddress      string
		Delay          string
		Status         int32
		IpInfo         *IpInfo
	}
	IpInfo struct {
		RegionName string `json:"regionName"`
		City       string `json:"city"`
		Country    string `json:"country"`
		Isp        string `json:"isp"`
		Org        string `json:"org"`
		Status     string `json:"status"`
	}
	//isp,city,org,country,status,regionName
)

var command = map[bool]string{true: "/system/bin/ping6", false: "/system/bin/ping"}

func Ping(ipAddress string, ttl byte, isIpv6 bool) (*Result, error) {
	result := &Result{IpLookupStatus: true}
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
					logger.Println(err)
					return nil, err
				}
				output, err := reader.ReadString('\n')
				if err != nil {
					logger.Println(err)
					return nil, err
				}
				outputs := strings.Split(output, " ")
				if len(outputs) < 2 {
					logger.Println("output less then 2 line")
					result.Status = -1
					return result, nil
				}
				if isIpv6 {
					result.IpAddress = outputs[1]
				} else {
					result.IpAddress = outputs[1][:len(outputs[1])-1]
				}
				return result, nil
			}
		}
		logger.Println(err)
		return nil, err
	}
	_, err = reader.ReadString('\n')
	if err != nil {
		logger.Println(err)
		return nil, err
	}
	if _, err = reader.Discard(14); err != nil {
		logger.Println(err)
		return nil, err
	}
	output, err := reader.ReadString('\n')
	if err != nil {
		logger.Println(err)
		return nil, err
	}
	outputs := strings.Split(output, " ")
	result.IpAddress = outputs[0][:len(outputs[0])-1]
	fmt.Println(outputs[3])
	delayStr := outputs[3][5:]
	result.Delay = strings.TrimSpace(delayStr)
	result.Status = 0
	return result, nil
}

func (r *Result) LookupIpInfo(isForce bool) (*IpInfo, error) {
	if !isForce && r.IpInfo != nil {
		return r.IpInfo, nil
	}
	res, err := http.Get(fmt.Sprintf("http://ip-api.com/json/%s?fields=isp,city,org,country,status,regionName", r.IpAddress))
	if err != nil {
		logger.Println(err)
		return nil, err
	}
	info := &IpInfo{}
	decoder := json.NewDecoder(res.Body)
	if err := decoder.Decode(info); err != nil {
		logger.Println(err)
		return nil, err
	}
	if info.Status != "success" {
		r.IpLookupStatus = false
		err = errors.New("Error look up IP")
		logger.Println(err)
		return nil, err
	}
	r.IpInfo = info
	return info, nil
}
